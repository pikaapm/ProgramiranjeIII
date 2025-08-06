**What it does:**
- Applies kernel filters (blur, sharpen, edge, identity) to PPM images.
- Works on a single .ppm file or a folder of .ppm files.
- Can chain operations, e.g. blur+edge or blur,edge.
- Modes: sequential, parallel, distributed, or a number of threads.
- Simple Swing GUI included.

**Requirements:**
- Java JDK 17+ (tested with JDK 24)
- Input format: PPM (.ppm)

**Build:**
javac ImageProcessor.java PpmImage.java Universal.java Worker.java Image.java GuiApp.java

**Run GUI:**
java GuiApp

**Output:**
- Single file: out/output_java<threads>.ppm
- Folder: out/output_<originalName>.ppm
