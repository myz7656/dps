package com.meituan.retail.dependencies

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

class Dependencies implements Plugin<Project> {

    static final int INIT_DEPTH = 1
    static final int DEPTH_STEP = 1
    static final int DEFAULT_COUNT = 1

    @Override
    void apply(Project project) {
        project.task("x-dependencies") {
            doLast {
                Configuration conf = project.configurations.getByName("_releaseApk")

                /**
                 * 构建依赖树，unique 用于保持唯一节点
                 */
                TreeNode tree = new TreeNode()
                Map<String, Map<String, NodeMeta>> unique = new HashMap<>()
                conf.incoming.resolutionResult.root.dependencies.each {
                    TreeNode node = visitResolvedDependencies(tree, it, unique, conf.incoming.artifacts)
                    tree.children.add(node)

                    mergeNodeMeta(tree.flattenedChild, node.flattenedChild)
                }

                /**
                 * 计算依赖大小：节点总大小、单独引入的大小
                 */
                tree.children.each {
                    calculateSize(it, tree)
                }

                /**
                 * 展示依赖树
                 */
                StringBuffer strTree = new StringBuffer()
                tree.children.each {
                    boolean last = (it == tree.children.last())
                    int format = make(0, last, INIT_DEPTH)
                    buildResolvedDependenciesTree(it, strTree, format, INIT_DEPTH)
                }
                print(strTree)

                /**
                 * 展示大小排行榜
                 */
                StringBuffer strSort = new StringBuffer()
                tree.flattenedChild.sort {
                    -it.value.size
                }.each {
                    strSort << it.key << " (" << it.value.size << " " << formatSize(it.value.size) << ")\n"
                }
                print(strSort)
            }
        }
    }

    static TreeNode visitResolvedDependencies(TreeNode parent, DependencyResult result, Map<String, Map<String, NodeMeta>> unique, ArtifactCollection artifacts) {
        TreeNode treeNode = new TreeNode()
        treeNode.parent = parent

        DependencyNode node = treeNode.node

        if (result instanceof ResolvedDependencyResult) {
            ResolvedDependencyResult r = result

            /**
             * 准备 DependencyNode 信息
             */
            node.id = r.selected.id.displayName
            node.group = r.selected.moduleVersion.group
            node.name = r.selected.moduleVersion.name
            node.version = r.selected.moduleVersion.version
            if (r.requested instanceof ModuleComponentSelector) {
                node.requestedVersion = ((ModuleComponentSelector)r.requested).version
            }
            artifacts.artifacts.each { ResolvedArtifactResult rar ->
                if (node.id == rar.getId().componentIdentifier.displayName) {
                    node.selfSize = rar.file.size()
                }
            }

            /**
             * 添加扁平化后的节点信息，添加子树结构
             * 1，这里如果子树如果已经解析过，则不再解析
             * 2，递归将子树的扁平化后的信息添加到父节点的扁平化信息中
             * 3，如果遇到已经解析过的子树节点，扁平化信息从全局的 map 中获取得到
             */
            mergeNodeMeta(treeNode.flattenedChild, node)

            if (!unique.containsKey(node.id)) {
                r.selected.dependencies.each {
                    TreeNode child = visitResolvedDependencies(treeNode, it, unique, artifacts)
                    treeNode.children.add(child)

                    mergeNodeMeta(treeNode.flattenedChild, child.flattenedChild)
                }
                unique.put(node.id, treeNode.flattenedChild)
            } else {
                Map<String, NodeMeta> flatten = unique.get(node.id)
                mergeNodeMeta(treeNode.flattenedChild, flatten)
            }
        } else {
            node.id = "could not resolve $result.requested.displayName"
            mergeNodeMeta(treeNode.flattenedChild, node)
        }

        return treeNode
    }

    static void mergeNodeMeta(Map<String, NodeMeta> to, Map<String, NodeMeta> from) {
        from.each {
            if (to.containsKey(it.key)) {
                NodeMeta meta = to.get(it.key)
                meta.count += it.value.count
            } else {
                to.put(it.key, new NodeMeta(it.value.count, it.value.size))
            }
        }
    }

    static void mergeNodeMeta(Map<String, NodeMeta> to, DependencyNode from) {
        if (to.containsKey(from.id)) {
            NodeMeta meta = to.get(from.id)
            meta.count++
        } else {
            to.put(from.id, new NodeMeta(DEFAULT_COUNT, from.selfSize))
        }
    }

    static void calculateSize(TreeNode treeNode, TreeNode root) {
        DependencyNode node = treeNode.node
        node.totalSize = 0L
        node.affectSize = 0L

        treeNode.flattenedChild.each {
            node.totalSize += it.value.size
        }

        treeNode.flattenedChild.each {
            if (it.key == node.id ||
                    it.value.count == DEFAULT_COUNT) {
                node.affectSize += it.value.size
            } else {
                NodeMeta rootNode = root.flattenedChild.get(it.key)
                NodeMeta selfNode = it.value
                if (rootNode != null && selfNode.count == rootNode.count) {
                    node.affectSize += it.value.size
                }
            }
        }

        treeNode.children.each {
            calculateSize(it, root)
        }
    }

    static void buildResolvedDependenciesTree(TreeNode treeNode, StringBuffer out, int format, int depth) {
        showResolvedDependencyNode(treeNode, out, format, depth)

        treeNode.children.each {
            boolean last = (it == treeNode.children.last())
            int dep = depth + DEPTH_STEP
            int f = make(format, last, dep)
            buildResolvedDependenciesTree(it, out, f, dep)
        }
    }

    private static void showResolvedDependencyNode(TreeNode treeNode, StringBuffer out, int format, int depth) {
        DependencyNode node = treeNode.node
        for (int i = 0; i < depth; ++i) {
            int actor = (0x1 << i)
            boolean last = (format & actor)
            if (last) {
                if (i == depth - DEPTH_STEP) {
                    out << "\\--- "
                } else {
                    out << "     "
                }
            } else {
                if (i == depth - DEPTH_STEP) {
                    out << "+--- "
                } else {
                    out << "|    "
                }
            }
        }

        out << node.group << ":" << node.name << ":" << node.requestedVersion
        if (node.version != node.requestedVersion) {
            out << " -> " << node.version
        }

        String sizeStr = formatSize(node.selfSize)
        String totalStr = formatSize(node.totalSize)
        String affectStr = formatSize(node.affectSize)
        out << "  (size:" << sizeStr

        if (node.totalSize > node.selfSize) {
            out << ", total:" << totalStr
        }

        if (node.affectSize > 0) {
            out << ", affect:" << affectStr
        }
        out << ")\n"
    }


    static int make(int format, boolean last, int depth) {
        int actor = 0
        if (last) {
            actor = (0x1 << (depth - DEPTH_STEP))
        }
        return (format | actor)
    }

    static String formatSize(long size) {
        double s = ((double)size) / 1024.0
        String unit = "KB"
        if (s > 1024.0) {
            s /= 1024.0
            unit = "MB"
        }
        return (String.format("%.2f", s) + unit)
    }
}