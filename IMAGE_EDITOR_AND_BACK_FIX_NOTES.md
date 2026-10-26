# Image editor and Back navigation update

- Image viewer includes a built-in non-destructive editor with crop presets (Original, 1:1, 4:3, 16:9), rotate 90°, horizontal flip, grayscale, brightness adjustment, Reset, and Save copy.
- ViewerActivity now handles Back explicitly: save video position, pause video/audio, clear PiP intent state, then finish the viewer so Android returns to the previous file-list activity.
- Source change only; compile/device runtime testing is still required.
