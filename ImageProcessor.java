import java.io.*;
import java.util.*;

//Kernel image processing class
//runs the program in distributed mode
public class ImageProcessor {

public static void main(String[] args) // Handle errors
       throws InterruptedException {

        if (args.length < 3) {
            System.out.println("Usage: java ImageProcessor <input file/folder> <output folder> <kernel1> [kernel2 kernel3...] <thread mode>"); //if the user did't give all of the arguments 
            // this instructions for arguments are printed (at least one kernel is needed, if more, use ',' or '+' between them'). If input will be a folder, it will use all of the images
            // in the folder, if it's a file, only that image and you can also choose how many threads you want to use sequential, parallel, distributed or just a number, if you don't specify, 
            // it will default to CPU count
            return;
        } 

        File inFile = new File(args[0]);
        String outputPath = args[1]; 
        File outDir = new File(outputPath);
        if (!outDir.exists() && !outDir.mkdirs()) {
            System.err.println("Could not create output directory: " + outDir.getAbsolutePath());
            return;
        }

        String[] ops = args[2].split("[,+]"); //if you want to use more than one kernel at once (you can use , or + between them in the instructions)

        int numThreads;  //this part uses the argument for the number od threads, if given
        if (args.length >= 4) {
            String maybeMode = args[3].toLowerCase();
            if (maybeMode.equals("sequential")) {  //if the argument is sequential use only one thread
            numThreads = 1;
        } else if (maybeMode.equals("parallel")) { //if the argument is parallel use the CPU count, but at least two threads
            numThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        } else if (maybeMode.equals("distributed")) {  // if the argument is distributed, use double the CPU count, but at least 4 threads
            numThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        } else {
            try {    // if none of the above is in defined, check if the number of threads is a number and use it
                numThreads = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {     //if it fails use the CPU count
                numThreads = Runtime.getRuntime().availableProcessors();
            }
        }
        } else {  //if there was no argument for the number of threads, just use the CPU count
            numThreads = Runtime.getRuntime().availableProcessors();
        }


        //for uploading the whole folder:

        if (inFile.isDirectory()) {
    File[] files = inFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".ppm"));  //read them all
    if (files != null) {
        for (File f : files) {
            PpmImage input = new PpmImage(), output, current; 
            input.ppmImport(f.getAbsolutePath());
            current = input;

            long start = System.currentTimeMillis(); //time used
            for (String opRaw : ops) {
                String op = opRaw.trim();
                output = new PpmImage(current.width, current.height, current.channels, current.depth);

            
                if (op.equalsIgnoreCase("mirror")) {
                    Worker.mirrorParallel(current, output, numThreads);
                } else {
                    float[] kernel = Universal.getKernelByName(op);
                    int kernelSize = (int) Math.sqrt(kernel.length);

                    if (numThreads == 1) {
                        Worker.applyKernelSingle(current, output, kernelSize, kernel);  //SEQUENTIAL 
                    } else {
                        Worker[] workers = new Worker[numThreads];
                        for (int i = 0; i < numThreads; i++)
                            workers[i] = new Worker(i, numThreads, current, output, kernelSize, kernel);
                        for (int i = 0; i < numThreads; i++)
                            workers[i].start();
                        for (Worker w : workers) w.join();
                    }
                }

                current = output; // chain to next operation
            }
            long end = System.currentTimeMillis();
            long elapsed = (end - start);
            System.out.print((double) elapsed / 1000);

            File outFile = new File(outDir, "output_" + f.getName());
                    current.ppmExport(outFile.getAbsolutePath()); //save
        }
    }
    return; 
}

        //for uploading a single image:

PpmImage input = new PpmImage(), output, current; //read just one image
input.ppmImport(args[0]);
current = input;

long start = System.currentTimeMillis(); //time used

for (String opRaw : ops) {
    String op = opRaw.trim();
    output = new PpmImage(current.width, current.height, current.channels, current.depth);

    if (op.equalsIgnoreCase("mirror")) {
        Worker.mirrorParallel(current, output, numThreads);
    } else {
        float[] kernel = Universal.getKernelByName(op);
        int kernelSize = (int) Math.sqrt(kernel.length);

         if (numThreads == 1) {
            Worker.applyKernelSingle(current, output, kernelSize, kernel);  //SEQUENTIAL 
            } else {
                Worker[] workers = new Worker[numThreads];
                for (int i = 0; i < numThreads; i++)
                     workers[i] = new Worker(i, numThreads, current, output, kernelSize, kernel);
                for (int i = 0; i < numThreads; i++)
                       workers[i].start();
                for (Worker w : workers) w.join();
            }
    }

    current = output; // chain to next operation
}

long end = System.currentTimeMillis();
long elapsed = (end - start);
System.out.print((double) elapsed / 1000); //print how long the process took in sc

File outFile = new File(outDir, "output_java" + numThreads + ".ppm");
        current.ppmExport(outFile.getAbsolutePath()); // save the image in a file
    }

}
