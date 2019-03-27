package com.meituan.retail.dependencies


/**
 * author: 后知后觉(307817387)
 * email: zhanyuanmin@meituan.com
 * time: 19/3/27 14:19
 */
class TreeNode {

    Map<String, NodeMeta> flattenedChild
    DependencyNode node

    TreeNode parent
    List<TreeNode> children

    TreeNode() {
        flattenedChild = new HashMap<>()
        node = new DependencyNode()
        parent = null
        children = new LinkedList<>()
    }
}
