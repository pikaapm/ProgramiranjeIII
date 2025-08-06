//Global class used to run the program in Sequential mode 
public class Universal {

    // a method to that holds name for all kernels to be created for performing feature extraction
     public static float[] identityKernel = {
        (float) 0, (float) 0, (float) 0,
        (float) 0, (float) 1, (float) 0,
        (float) 0, (float) 0, (float) 0
    };

// kernel blurs the images
   public static float[] blurKernel = {
        (float) 1 / 9, (float) 1 / 9, (float) 1 / 9,
        (float) 1 / 9, (float) 1 / 9, (float) 1 / 9,
        (float) 1 / 9, (float) 1 / 9, (float) 1 / 9
    };

    // Kernel shaprens the images
   public static float[] sharpenKernel = {
        (float) 0, (float) -1, (float) 0,
        (float) -1, (float) 5, (float) -1,
        (float) 0, (float) -1, (float) 0
    };

    // showing the edges
    public static float[] edgeKernel = {
        (float) -1, (float) -1, (float) -1,
        (float) -1, (float) 8, (float) -1,
        (float) -1, (float) -1, (float) -1
    };

    //this part makes it possible for the USER to specify which kernel to use (what change they want applied to the image)
    public static float[] getKernelByName(String name) {
        switch (name.toLowerCase()) {   //if you write BLUR or Blur for example, it will work for blur
            case "blur": return blurKernel;
            case "sharpen": return sharpenKernel;
            case "edge": return edgeKernel;
            default: return identityKernel;  // if non of the methods above is chosen, it gives back the identityKernel (does nothinf)
        }
    }
    }



