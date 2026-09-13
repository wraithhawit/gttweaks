# Submenus remain visible after closing: removal fails

## Evidence

The user confirmed 0.27 still stacks submenus and preserves them after closing. At 07:53:53.569 and 07:53:54.825 on September 12, latest.log reports respectively `attached=2, retained=2, detached=0` and `attached=3, retained=2, detached=0`. The earlier menu buttons have `open=false`, yet their menus remain in the panel's tree.

The ModularUI jar extracted from the live instance's GT jar exactly matches the staged dependency (SHA256 `0DE8AE778722065F006028396E7A29EAB19218C774774C7DF51C6143BBB58EB1`).

`Widget.equals(Object)` first checks whether the other object's exact class is `Widget.class`; otherwise it returns false. A Menu is a subclass, so even `menu.equals(menu)` is false. `AbstractParentWidget.remove(IWidget)` uses `List.remove(Object)`, which calls this broken equals method and returns false. The caller then marks the menu button closed without detaching the menu.

This is reproduced using real Menu instances in `WidgetRemovalTest`, with only ModularUI's environment accessor mocked for headless construction. The test confirms self-equality fails, ordinary list removal fails, and identity removal succeeds without removing a sibling or root. It also checks reopening/removing the same cached menu.

## Fix and limits

`WidgetRemovalMixin` redirects the List.remove(Object) call inside the declaring class's `remove(IWidget)` method to `WidgetRemoval.removeIdentity`. It retains the existing disposal and onChildRemove callbacks. It does not replace Widget.equals globally or change input routing.

The branch-selection tests alone in 0.27 did not exercise actual widget removal. All five tests now pass, including the actual-Menu reproduction. The mixin descriptor is verified against bytecode, but runtime application and visual behavior still need verification after restarting with 0.28.

Open X, open one ability's submenu, then another. The old column should disappear. Close X and reopen it: no stale submenu should remain. Repeat with C/M and switch preview pages. Log entries should no longer show closed submenus accumulating.
