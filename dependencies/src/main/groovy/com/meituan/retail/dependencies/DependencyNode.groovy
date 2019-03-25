package com.meituan.retail.dependencies

class DependencyNode implements Serializable {

    DependencyNode parent = null

    String group = ""
    String name = ""
    String version = ""

    String id = ""
    String requestedVersion = ""

    boolean seenBefore = false
    List<DependencyNode> children = new ArrayList<DependencyNode>()

    long selfSize = 0L
    long totalSize = 0L
    long affectSize = 0L

    boolean equals(o) {
        if (this.is(o)) return true
        if (!(o instanceof DependencyNode)) return false

        final DependencyNode that = (DependencyNode) o

        if (group != that.group) return false
        if (name != that.name) return false
        if (version != that.version) return false

        return true
    }

    int hashCode() {
        int result
        result = (group != null ? group.hashCode() : 0)
        result = 31 * result + (name != null ? name.hashCode() : 0)
        result = 31 * result + (version != null ? version.hashCode() : 0)
        return result
    }


    @Override
    public String toString() {
        return "DependencyNode {" + '\n' +
                "  group='" + group + '\'' + '\n' +
                "  name='" + name + '\'' + ',' + '\n' +
                "  version='" + version + '\'' + ',' + '\n' +
                "  id='" + id + '\'' + ',' + '\n' +
                "  requestedVersion='" + requestedVersion + '\'' + ',' + '\n' +
                "  selfSize=" + selfSize + '\n' +
                '}'
    }
}