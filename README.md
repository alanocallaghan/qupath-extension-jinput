# QuPath jinput extension

Welcome to the jinput extension for [QuPath](http://qupath.github.io)!

This QuPath extension adds advanced input controller support for browsing whole slide images,
using the [jinput](https://jinput.github.io/jinput/) API for game controller discovery and polled input.


Currently, this will be tested with the [SpaceMouse pro](https://3dconnexion.com/uk/product/spacemouse-pro/)
from 3Dconnexion (by @zindy), but was originally designed for (and only tested with) the now
discontinued [SpaceNavigator](https://spacemice.org/index.php?title=SpaceNavigator).

However, it does not make use of most of the 3D features, and therefore *may* work with other similar
input controllers, including joysticks (and it may not).

The extension is intended for the (at the time of writing) not-yet-released 
QuPath v0.5.
It is not compatible with earlier QuPath versions.


## Installing

If you haven't installed any extensions before, you'll be prompted to select a QuPath user directory.
The extension will then be copied to a location inside that directory.

You might then need to restart QuPath (but not your computer).


## Building

You can build the extension with

```bash
gradlew clean shadowJar
```

The output will be under `build/libs`.

This should include the extension, *jinput* and its associated native libraries in a single jar file that can be dragged on top of QuPath for installation in the extensions directory.

> Note that you need `shadowJar` rather than `build` to include the advanced input library itself (TODO - need to check this).
