package com.meituan.gradle.dps


/**
 * author: 后知后觉(307817387)
 * email: zhanyuanmin@meituan.com
 * time: 19/3/27 14:19
 */
class TreeNode {

    DependencyNode node

    Map<String, NodeMeta> flattenedChild
    List<TreeNode> children

    TreeNode() {
        node = new DependencyNode()

        flattenedChild = new HashMap<>()
        children = new LinkedList<>()
    }
}
