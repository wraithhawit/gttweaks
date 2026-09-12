package com.wraithhawit.gttweaks;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MenuBranchSelectionTest {
    static class Node {
        final List<Node> children = new ArrayList<>();
        Node source;
        Node child(Node child) { children.add(child); return this; }
    }

    private Set<Node> retained(Node root, Node incoming, Node... attached) {
        return MenuBranchSelection.retained(root, incoming, List.of(attached),
                node -> node.children, node -> node.source);
    }

    @Test void switchingSiblingKeepsRootAndNewMenuOnly() {
        Node firstButton = new Node(), secondButton = new Node();
        Node root = new Node().child(new Node().child(firstButton).child(secondButton));
        Node first = new Node(), second = new Node();
        first.source = firstButton;
        second.source = secondButton;
        assertEquals(Set.of(root, second), retained(root, second, root, first));
        // Reopening the first choice must discard the second, even with stale menus attached.
        assertEquals(Set.of(root, first), retained(root, first, root, first, second));
    }

    @Test void deeperMenuKeepsItsAncestorButClosesOtherBranch() {
        Node button = new Node(), nestedButton = new Node();
        Node root = new Node().child(button);
        Node parent = new Node().child(nestedButton);
        parent.source = button;
        Node deeper = new Node();
        deeper.source = nestedButton;
        Node stale = new Node();
        assertEquals(Set.of(root, parent, deeper), retained(root, deeper, root, parent, stale));
    }

    @Test void returningToRootClosesPreviousDescendants() {
        Node button = new Node(), other = new Node(), nestedButton = new Node();
        Node root = new Node().child(button).child(other);
        Node parent = new Node().child(nestedButton);
        parent.source = button;
        Node deeper = new Node();
        deeper.source = nestedButton;
        Node replacement = new Node();
        replacement.source = other;
        assertEquals(Set.of(root, replacement), retained(root, replacement, root, parent, deeper));
    }

    @Test void wrappedMenuIsFoundByIdentity() {
        Node menu = new Node();
        Node wrapper = new Node().child(menu);
        assertTrue(MenuBranchSelection.contains(wrapper, menu, node -> node.children));
        assertFalse(MenuBranchSelection.contains(wrapper, new Node(), node -> node.children));
    }
}
