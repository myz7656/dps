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

    @Override
    void apply(Project project) {
        project.task("x-dependencies") {
            doLast {
                Configuration conf = project.configurations.getByName("_releaseApk")

                /**
                 * 构建依赖树，exist 用于保持唯一节点，内部会标记 duplicate 属性
                 */
                Set<DependencyNode> exist = new LinkedHashSet<>()
                DependencyNode root = new DependencyNode()
                conf.incoming.resolutionResult.root.dependencies.each {
                    DependencyNode node = visitResolvedDependencies(root, it, exist, conf.incoming.artifacts)
                    root.children.add(node)
                }

                /**
                 * 标记重复节点
                 */
                markDuplicate(root, exist)

                /**
                 * 计算依赖大小：节点总大小、单独引入的大小
                 */
                calculateSize(root, exist)

                /**
                 * 展示依赖树
                 */
                StringBuffer out = new StringBuffer()
                root.children.each {
                    boolean last = (it == root.children.last())
                    int format = make(0, last, INIT_DEPTH)
                    buildResolvedDependenciesTree(it, out, format, INIT_DEPTH)
                }
                print(out)
            }
        }
    }

    static DependencyNode visitResolvedDependencies(DependencyNode parent, DependencyResult result, Set<DependencyNode> exist, ArtifactCollection artifacts) {
        DependencyNode node = new DependencyNode()
        if (result instanceof ResolvedDependencyResult) {
            ResolvedDependencyResult r = result

            node.parent = parent

            node.group = r.selected.moduleVersion.group
            node.name = r.selected.moduleVersion.name
            node.version = r.selected.moduleVersion.version
            node.id = r.selected.id.displayName

            artifacts.artifacts.each { ResolvedArtifactResult rar ->
                if (node.id == rar.getId().componentIdentifier.displayName) {
                    node.selfSize = rar.file.size()
                }
            }

            if (r.requested instanceof ModuleComponentSelector) {
                node.requestedVersion = ((ModuleComponentSelector)r.requested).version
            }

            if (exist.add(node)) {
                r.selected.dependencies.each {
                    DependencyNode childNode = visitResolvedDependencies(node, it, exist, artifacts)
                    node.children.add(childNode)
                }
            } else {
                node.duplicate = true
                exist.each {
                    if (it == node) {
                        it.duplicate = true
                    }
                }
            }
        } else {
            node.parent = parent
            node.name = "Could not resolve $result.requested.displayName"
        }

        return node
    }

    static void markDuplicate(DependencyNode node, Set<DependencyNode> exist) {
        if (!node.duplicate) {
            for (DependencyNode it : exist) {
                if (it == node && it.duplicate) {
                    node.duplicate = true
                    break
                }
            }
        }

        node.children.each {
            markDuplicate(it, exist)
        }
    }

    static calculateSize(DependencyNode node, Set<DependencyNode> exist) {
        node.totalSize = node.selfSize
        node.affectSize = node.selfSize
        if (node.duplicate) {
            node.affectSize = 0
        }

        node.children.each {
            def (total, affect) = calculateSize(it, exist)
            node.totalSize += total
            node.affectSize += affect
        }
        return [node.totalSize, node.affectSize]
    }

    static void buildResolvedDependenciesTree(DependencyNode node, StringBuffer out, int format, int depth) {
        showResolvedDependencyNode(node, out, format, depth)

        node.children.each {
            boolean last = (it == node.children.last())
            int dep = depth + DEPTH_STEP
            int f = make(format, last, dep)
            buildResolvedDependenciesTree(it, out, f, dep)
        }
    }

    private static void showResolvedDependencyNode(DependencyNode node, StringBuffer out, int format, int depth) {
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
        if (node.children.size() > 0) {
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