package com.meituan.retail.dependencies

class DependencyNode {
    String id = ""

    String group = ""
    String name = ""
    String version = ""

    String requestedVersion = ""
    long selfSize = 0L

    long totalSize = 0L
    long affectSize = 0L

    boolean equals(o) {
        if (this.is(o)) return true
        if (!(o instanceof DependencyNode)) return false

        final DependencyNode that = (DependencyNode) o

        if (id != that.id) return false

        return true
    }

    int hashCode() {
        return (id != null ? id.hashCode() : 0)
    }

    @Override
    String toString() {
        return "DependencyNode {" + '\n' +
                "  id='" + id + '\'' + ',' + '\n' +
                "  group='" + group + '\'' + '\n' +
                "  name='" + name + '\'' + ',' + '\n' +
                "  version='" + version + '\'' + ',' + '\n' +
                "  requestedVersion='" + requestedVersion + '\'' + ',' + '\n' +
                "  selfSize=" + selfSize + '\n' +
                '}'
    }
}