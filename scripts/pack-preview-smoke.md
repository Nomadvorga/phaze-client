# Pack preview placement smoke check

Build with `./gradlew build --offline` (Windows: `gradlew.bat build --offline`).
Use the resulting `build/libs/PhazeClient-1.2.jar` in a Minecraft 1.21.4 Fabric client.

1. Enable Pack Organizer and Pack Previews using the UI; do not edit the saved config manually.
2. Open the resource pack screen with several packs in both lists.
3. Hover a pack: its name and description must disappear, replaced by two rows
   of textures and the four status lines in the same text area to the right
   of its icon. Nothing should appear over the screen header or search field.
4. Move to a different pack: the first pack's name/description must return and
   only the newly hovered pack should show a preview.
5. Scroll each list, including partially visible entries at the top/bottom.
   The preview must follow its entry and stay clipped to the list viewport.
6. Hover compatible and incompatible packs, then use the icon controls to
   enable, disable and reorder packs. Icon controls and the incompatible-pack
   confirmation must still work.
7. Disable Pack Previews, then disable Pack Organizer: in both cases the normal
   pack names/descriptions should render without a preview.
8. Repeat at different GUI scales. Both icon rows, the divider and all status
   text must fit inside the entry without overlapping the scrollbar or the
   adjacent list.

These are additional manual checks. Automated scanner/search tests are included
in `build --offline`. `runPackPreviewSmoke --offline` runs the actual client in
`build/pack-preview-client-test/`, verifies folder/ZIP detection and isolation from
an enabled overriding pack, exercises the search field, and saves screenshots.
