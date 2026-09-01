# Conversion notes

## Carried over from the HTML prototype
- Library, Desk, Profile as the three tabs; writing reached through a story
- Continue-writing card on the Library screen
- Entry kinds (chapter, prologue, epilogue, bio, author's note)
- Chapter numbering that counts only chapters, so a prologue or A/N never
  pushes chapter one to two
- Author's notes excluded from every word total
- Word counting that treats CJK as one word per character
- Six accent colours, stored on the profile
- Trash with restore and delete-forever
- JSON backup in the same shape as the web vault

## Deliberately not carried over yet
- Rich text. The Compose editor is plain text for this pass; imported HTML is
  flattened. Formatting is the next major layer.
- Cover and banner images.
- Reader mode, read-aloud, sprints, version history, EPUB export.

## Deliberately dropped
- All browser/Capacitor storage detection, permission probing, and the vault
  tiering. None of it has a purpose once storage is a file the app owns.
- MANAGE_EXTERNAL_STORAGE. SAF covers backups without asking for anything.
