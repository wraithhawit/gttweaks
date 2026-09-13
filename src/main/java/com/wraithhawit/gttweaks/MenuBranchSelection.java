package com.wraithhawit.gttweaks;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Resolves ownership from attached children, without consulting invalid widget parent pointers. */
public final class MenuBranchSelection {
    private MenuBranchSelection() {}

    public static <T> Set<T> retained(T root, T incoming, List<T> attached,
                                     Function<T, List<T>> children, Function<T, T> source) {
        Set<T> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        keep.add(root);
        keep.add(incoming);
        T current = incoming;
        while (current != root) {
            T button = source.apply(current);
            if (button == null) break;
            T owner = null;
            for (T candidate : attached) {
                if (candidate != current && contains(candidate, button, children)) {
                    owner = candidate;
                    break;
                }
            }
            if (owner == null || !keep.add(owner)) break;
            current = owner;
        }
        return keep;
    }

    public static <T> boolean contains(T tree, T target, Function<T, List<T>> children) {
        if (tree == target) return true;
        for (T child : children.apply(tree)) {
            if (contains(child, target, children)) return true;
        }
        return false;
    }
}
