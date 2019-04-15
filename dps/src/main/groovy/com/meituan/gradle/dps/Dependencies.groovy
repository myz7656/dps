package com.meituan.gradle.dps

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

class DepExtensions {
    boolean sort = false
}

class Dependencies implements Plugin<Project> {

    static final int INIT_DEPTH = 1
    static final int DEPTH_STEP = 1
    static final int DEFAULT_COUNT = 1

    @Override
    void apply(Project project) {
        project.extensions.create("dps", DepExtensions)
        project.task("dps") {
            doLast {
                DepExtensions ext = project.extensions.getByName("dps")
                if (ext == null) {
                    ext = new DepExtensions()
                }

                project.configurations.each {
                    println(it.name + " - " + it.description)
                    runConfiguration(it, ext.sort)
                }
            }
        }
    }

    static void runConfiguration(Configuration conf, boolean sort) {
        /**
         * 准备文件大小
         */
        Map<String, File> flattenedFiles = new HashMap<>()
        conf.incoming.artifacts.each {
            flattenedFiles.put(it.id.componentIdentifier.displayName, it.file)
        }

        /**
         * 构建依赖树，flattenedTree 用于保持唯一节点
         */
        TreeNode tree = new TreeNode()
        Map<String, TreeNode> flattenedTree = new HashMap<>()
        conf.incoming.resolutionResult.root.dependencies.each {
            TreeNode node = buildResolvedDependenciesTree(it, flattenedTree, flattenedFiles)
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
        tree.children.each {
            boolean last = (it == tree.children.last())
            int format = make(0, last, INIT_DEPTH)
            showResolvedDependenciesTree(it, format, INIT_DEPTH)
        }
        if (tree.children.size() == 0) {
            println("No dependencies")
        }
        println()

        /**
         * 展示大小排行榜
         */
        if (sort && tree.flattenedChild.size() > 0) {
            println("flattened - show flattened dependencies, sort by size")
            tree.flattenedChild.sort {
                -it.value.size
            }.each {
                println(it.key + " (" + it.value.size + " " + formatSize(it.value.size) + ")")
            }
            println()
        }
    }

    static TreeNode buildResolvedDependenciesTree(DependencyResult result, Map<String, TreeNode> flattenedTree, Map<String, File> flattenedFiles) {
        TreeNode treeNode = new TreeNode()
        if (result instanceof ResolvedDependencyResult) {
            ResolvedDependencyResult r = result

            prepareNode(treeNode.node, r, flattenedFiles)
            String id = treeNode.node.id
            if (!flattenedTree.containsKey(id)) {
                treeNode.flattenedChild.put(id, new NodeMeta(DEFAULT_COUNT, treeNode.node.selfSize))
                r.selected.dependencies.each {
                    TreeNode child = buildResolvedDependenciesTree(it, flattenedTree, flattenedFiles)
                    treeNode.children.add(child)

                    mergeNodeMeta(treeNode.flattenedChild, child.flattenedChild)
                }
                flattenedTree.put(id, treeNode)
            } else {
                TreeNode exist = flattenedTree.get(id)
                treeNode.flattenedChild = exist.flattenedChild
            }

        } else {
            treeNode.node.id = "could not resolve $result.requested.displayName"
        }

        return treeNode
    }

    static void prepareNode(DependencyNode node, ResolvedDependencyResult r, Map<String, File> files) {
        node.id = r.selected.id.displayName
        node.group = r.selected.moduleVersion.group
        node.name = r.selected.moduleVersion.name
        node.version = r.selected.moduleVersion.version
        if (r.requested instanceof ModuleComponentSelector) {
            node.requestedVersion = ((ModuleComponentSelector)r.requested).version
        }
        File file = files.get(node.id)
        if (file != null) {
            node.selfSize = file.size()
        }
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

    static void calculateSize(TreeNode treeNode, TreeNode root) {
        DependencyNode node = treeNode.node
        node.totalSize = 0L
        node.affectSize = 0L

        treeNode.flattenedChild.each {
            node.totalSize += it.value.size
        }

        treeNode.flattenedChild.each {
            if (it.value.count == DEFAULT_COUNT) {
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

    static void showResolvedDependenciesTree(TreeNode treeNode, int format, int depth) {
        showResolvedDependencyNode(treeNode, format, depth)

        treeNode.children.each {
            boolean last = (it == treeNode.children.last())
            int dep = depth + DEPTH_STEP
            int f = make(format, last, dep)
            showResolvedDependenciesTree(it, f, dep)
        }
    }

    private static void showResolvedDependencyNode(TreeNode treeNode, int format, int depth) {
        StringBuffer out = new StringBuffer()
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
        out << ")"
        println(out)
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