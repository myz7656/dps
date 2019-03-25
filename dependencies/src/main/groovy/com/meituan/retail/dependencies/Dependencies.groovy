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
    static final TAB_SIZE = 4

    @Override
    void apply(Project project) {
        project.task("x-dependencies") {
            doLast {
                Configuration conf = project.configurations.getByName("_releaseApk")

                /**
                 * 构建依赖树
                 */
                Set<DependencyNode> exist = new LinkedHashSet<>()
                DependencyNode root = new DependencyNode()
                conf.incoming.resolutionResult.root.dependencies.each { DependencyResult dr ->
                    DependencyNode node = visitResolvedDependencies(root, dr, exist, conf.incoming.artifacts)
                    root.children.add(node)
                }

                /**
                 * 计算依赖大小：节点总大小、单独引入的大小
                 */
                calculateSize(root)

                /**
                 * 展示依赖树
                 */
                final out = new StringBuffer()
                buildResolvedDependenciesTree(root, out, 0, false)
                print(out)

                out = new StringBuffer()
                exist.each {
                    if (it.version != it.requestedVersion)
                        out << it << "\n"
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

            artifacts.artifacts.each { ResolvedArtifactResult ar ->
                if (node.id == ar.getId().componentIdentifier.displayName) {
                    node.selfSize = ar.file.size()
                }
            }

            if (r.requested instanceof ModuleComponentSelector) {
                node.requestedVersion = ((ModuleComponentSelector)r.requested).version
            }

            if (exist.add(node)) {
                r.selected.dependencies.each { DependencyResult subResult ->
                    DependencyNode childNode = visitResolvedDependencies(node, subResult, exist, artifacts)
                    node.children.add(childNode)
                }
            } else {
                node.seenBefore = true
            }
        } else {
            node.parent = parent
            node.name = "Could not resolve $result.requested.displayName"
        }

        return node
    }

    static void calculateSize(DependencyNode root) {

    }

    static void buildResolvedDependenciesTree(DependencyNode root, StringBuffer out, int depth, boolean last) {
        for (int i = 0; i < depth; i++) out << "|    "

        if (!last) {
            out << "+--- "
        } else {
            out << "\\--- "
        }

        out << root.group << ":" << root.name << ":" << root.requestedVersion
        if (root.version != root.requestedVersion) {
            out << " -> " << root.version
        }
        out << "  (total:" << root.totalSize << ", affect:" << root.affectSize << ")\n"

        int size = root.children.size()
        for (int i = 0; i < size; ++i) {
            boolean l = (i == (size - 1))
            DependencyNode node = root.children.get(i)
            buildResolvedDependenciesTree(node, out, depth + 1, l)
        }
    }
}