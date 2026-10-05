# Connected follow-up check

- Swap active hands repeatedly with storage open: hands and stored items should not blink away.
- Open worn bag, belt and ground bag: compact windows start above the hotbar. Check the larger X and grip; drag from anywhere on the left tab except X. Move worn windows, close/reopen them, then check remembered positions and the three-visible-window limit.
- Overlap windows: lower items, counts and previews must not show through the upper window or its frame.
- Drag an ordinary item from each hand onto the worn bag/belt slot. Drag stored items onto each empty hand slot while that destination is inactive. Drag held items between hands in both directions; active selection should not change. Occupied/full destinations and stale sources must preserve both owners.
- Hold a bag in one hand and an ordinary item in the other. Drag the item onto the bag's hand slot (not its grid); repeat in both directions and with the source hand inactive. Full bags and occupied non-storage hands must not consume or drop the source.
- For quick world storage, click an empty grid cell while holding an item; select an empty hand and click a stored item to take it. R and right-click should rotate the same ongoing drag; right release must not commit.
- With bag/belt windows open, unequip, move between hands, re-equip, drop and pick up the same storage item. The same window should remain in place, including with three windows open. Then move a ground bag out of reach or destroy it: its window should close. Check later transfers still work.
- Enter free-cursor mode with L-alt and no storage open. Wait ten seconds: it should stay active until you dismiss it. Chat remains visible at the slightly larger text size. Check your usual GUI scales.
- Hold an empty storage cell to preview insertion for ten seconds, then rotate/move/release. Repeat with worn, held and ground storage. Refreshes must not erase the preview; invalid or stale operations must preserve the source.
- Specifically keep the pickaxe in hand, press an empty bag cell and hold for ten seconds without starting from the hand icon. Then drag/rotate/release that same proposal. Compare with dragging an already stored child.
- Q-drop toward ground and near walls: aimed placement is shorter, but roof/crowding/wall fallback still drops without losing the item.
