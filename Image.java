
//class to be define  the properties of an image
public class Image {
//width of the image
    int width;
//height  of the image
    int height;
    
// color depth of the image RGBA
    int channels;
    
    //Color depth is equivalent to bit depth or pixel depth
    int depth;
    // Data associated with the  The Image
    float[] data;

    Image() {
        this.width = 0;
        this.height = 0;
        this.channels = 3;
        this.depth = 0;
        this.data = null;
    }

}
