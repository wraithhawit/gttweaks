# gttweaks 0.25.0: EMI preview menus

> **Failed in-game verification:** the preview is stuck in its initial isometric view and the C/M/X predicate menus do nothing. Do not release this build or treat the mocked routing tests as runtime coverage.

The previous outside-click fix used a global weak reference to the last embedded screen drawn. It ignored the actual click coordinates, relied on old hover state, and did not close cached dropdowns when EMI changed pages. A dismissed menu could also remain in ModularUI's dispatch snapshot until the next frame.

This build:

- Finds GregTech ModularUI screens through EMI's current page and the existing screen cache. It never constructs a screen for input or cleanup.
- Refreshes transformed hit-testing with the actual press coordinates and delivers the press once. Dropdowns receive clicks outside their recipe box; dismissal does not click through to an EMI recipe.
- Refreshes the open-panel snapshot between menu dismissal and dispatch, allowing a different menu button to receive the same press.
- Closes cached dropdowns immediately on page change, resize, or removal of the EMI screen. The preview itself stays cached.
- Makes Escape dismiss an open dropdown first. Keyboard input goes to a focused menu field before EMI search.
- Detaches nested menus before the root closes and invalidates their source buttons; always retains the cached root menu.

The cleanroom count fix, preview camera fit, and existing panel transforms are retained. The off-centre 3D rotation remains a separate unresolved issue. No KubeJS recipes or pack configs change.

Validation: `gradlew.bat build --offline --console=plain`; seven passing JUnit/Mockito tests exercise routing, current-page ownership, pointer refresh order, and immediate menu cleanup. These do not run mixin transformation or draw a client UI; runtime application and visual behavior still require verification.

In ATM10(3), open a stock Electric Blast Furnace's multiblock preview, then a large ported multiblock:

1. Open a letter dropdown and a nested submenu; move to another branch. Only the current branch should remain.
2. Click another letter button. The old dropdown should close and the clicked button should respond.
3. Choose an entry outside the preview box; then click outside the menu to dismiss it.
4. Edit a numeric field. Typing should affect the field, not EMI search.
5. Press Escape with a dropdown open. It should dismiss the menu before leaving the recipe screen.
6. Switch EMI pages or categories, then return. Old dropdowns should be closed. Repeat after closing/reopening EMI and changing GUI scale.

First-use log markers:

`[gttweaks] EMI dropdown click routed to current page with refreshed pointer`

`[gttweaks] closed cached EMI dropdowns immediately; main preview retained`

The markers establish execution, not visual correctness. Record observed behavior and any exception before declaring the menus fixed in game.
