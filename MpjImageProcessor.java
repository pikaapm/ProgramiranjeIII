// Run with MPJ Express
import mpi.*;     // MPJ Express
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MpjImageProcessor {

    static final int ROOT = 0;

    // --- map op name <-> small code (broadcast ints, like in examples) ---
    private static int opCode(String s) {
        s = s.toLowerCase().trim();
        if (s.equals("mirror"))  return 1;
        if (s.equals("blur"))    return 2;
        if (s.equals("sharpen")) return 3;
        if (s.equals("edge"))    return 4;
        return 0; // identity
    }
    private static float[] kernelFor(int code) {
        switch (code) {
            case 2: return Universal.blurKernel;
            case 3: return Universal.sharpenKernel;
            case 4: return Universal.edgeKernel;
            default: return Universal.identityKernel;
        }
    }

    // --- helpers (compute rows [r0,r1)) ---
    private static void applyKernelRows(PpmImage in, float[] outFull, int kSize, float[] ker, int r0, int r1) {
        int W = in.width, H = in.height, C = in.channels;
        for (int i = r0; i < r1; i++) {
            for (int j = 0; j < W; j++) {
                for (int c = 0; c < C; c++) {
                    float sum = 0f;
                    for (int ii = 0; ii < kSize; ii++) {
                        int ik = i - kSize/2 + ii;
                        if (ik < 0) ik = 0; else if (ik >= H) ik = H - 1;
                        for (int jj = 0; jj < kSize; jj++) {
                            int jk = j - kSize/2 + jj;
                            if (jk < 0) jk = 0; else if (jk >= W) jk = W - 1;
                            int idx = (ik * W + jk) * C + c;
                            int kk  = (kSize - 1 - ii) * kSize + (kSize - 1 - jj);
                            sum += in.data[idx] * ker[kk];
                        }
                    }
                    outFull[(i * W + j) * C + c] = sum;
                }
            }
        }
    }
    private static void mirrorRows(PpmImage in, float[] outFull, int r0, int r1) {
        int W = in.width, C = in.channels;
        for (int i = r0; i < r1; i++) {
            for (int j = 0; j < W; j++) {
                int dx = W - 1 - j;
                int sIdx = (i * W + j)  * C;
                int dIdx = (i * W + dx) * C;
                for (int c = 0; c < C; c++) outFull[dIdx + c] = in.data[sIdx + c];
            }
        }
    }

    private static int[] rowRangeEven(int H, int rank, int size) {
        int rowsPer = H / size;
        int start = rank * rowsPer;
        int end   = start + rowsPer;
        return new int[]{start, end};
    }

    public static void main(String[] args) throws Exception {
        MPI.Init(args); // Example1 style init/finalize.  // ref: Example1Init
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        if (rank == ROOT && args.length < 3) {
            System.out.println("Usage: mpjrun -np <P> MpjImageProcessor <input file/folder> <output folder> <ops>");
            MPI.Finalize();
            return;
        }

        // Parse the LAST three args (mpjrun may prepend its own)
        String inPath = (args.length >= 3) ? args[args.length - 3] : null;
        String outDirPath = (args.length >= 2) ? args[args.length - 2] : null;
        String opsStr = (args.length >= 1) ? args[args.length - 1] : null;

        // Prepare file list on root
        List<String> list = new ArrayList<>();
        if (rank == ROOT) {
            File outDir = new File(outDirPath);
            outDir.mkdirs();
            File in = new File(inPath);
            if (in.isDirectory()) {
                File[] fs = in.listFiles((d, n) -> n.toLowerCase().endsWith(".ppm"));
                if (fs != null) for (File f : fs) list.add(f.getAbsolutePath());
            } else {
                list.add(in.getAbsolutePath());
            }
        }

        // Broadcast number of files (Example2Broadcast pattern).  // ref: Example2Broadcast
        int[] nFilesArr = new int[]{rank == ROOT ? list.size() : 0};
        MPI.COMM_WORLD.Bcast(nFilesArr, 0, 1, MPI.INT, ROOT);
        int nFiles = nFilesArr[0];

        // Broadcast ops as small codes (also Example2 style bcast)
        String[] toks = (rank == ROOT) ? opsStr.split("[,+]") : new String[0];
        int[] opCodes = new int[(rank == ROOT) ? toks.length : 1];
        if (rank == ROOT) for (int i = 0; i < toks.length; i++) opCodes[i] = opCode(toks[i]);
        int[] nOpsArr = new int[]{(rank == ROOT) ? opCodes.length : 0};
        MPI.COMM_WORLD.Bcast(nOpsArr, 0, 1, MPI.INT, ROOT);
        int nOps = nOpsArr[0];
        if (rank != ROOT) opCodes = new int[nOps];
        MPI.COMM_WORLD.Bcast(opCodes, 0, nOps, MPI.INT, ROOT);

        long t0All = System.currentTimeMillis();

        for (int fi = 0; fi < nFiles; fi++) {

            // ROOT loads image and broadcasts header + pixels
            PpmImage curr = new PpmImage();
            int[] hdr = new int[4]; // W,H,C,depth
            if (rank == ROOT) {
                curr.ppmImport(list.get(fi));
                hdr[0] = curr.width; hdr[1] = curr.height; hdr[2] = curr.channels; hdr[3] = curr.depth;
            }
            MPI.COMM_WORLD.Bcast(hdr, 0, 4, MPI.INT, ROOT);           // header
            if (rank != ROOT) curr = new PpmImage(hdr[0], hdr[1], hdr[2], hdr[3]);
            MPI.COMM_WORLD.Bcast(curr.data, 0, curr.data.length, MPI.FLOAT, ROOT); // pixels

            // Even split for Gather (like Example4Gather). Remainder handled on ROOT.
            int rowsPer = curr.height / size;
            int[] rr = rowRangeEven(curr.height, rank, size);
            int r0 = rr[0], r1 = rr[1];
            int blockCount = Math.max(0, (r1 - r0) * curr.width * curr.channels);

            for (int oi = 0; oi < nOps; oi++) {
                int code = opCodes[oi];
                float[] outFullLocal = new float[curr.data.length];

                long tStart = System.currentTimeMillis();

                if (r1 > r0) {
                    if (code == 1) {
                        mirrorRows(curr, outFullLocal, r0, r1);
                    } else {
                        float[] ker = kernelFor(code);
                        int kSize = (int)Math.sqrt(ker.length);
                        applyKernelRows(curr, outFullLocal, kSize, ker, r0, r1);
                    }
                }

                // Prepare my contiguous chunk for Gather (equal sizes)
                float[] sendChunk = new float[blockCount];
                if (blockCount > 0) {
                    System.arraycopy(outFullLocal, r0 * curr.width * curr.channels, sendChunk, 0, blockCount);
                }

                // Gather equal chunks on ROOT (Example4Gather style).  // ref: Example4Gather
                float[] gathered = null;
                if (rank == ROOT) gathered = new float[size * blockCount];
                MPI.COMM_WORLD.Gather(sendChunk, 0, blockCount, MPI.FLOAT,
                                      gathered,  0, blockCount, MPI.FLOAT, ROOT);

                // ROOT builds the next full image (gathered part + leftover rows)
                float[] nextData = null;
                if (rank == ROOT) {
                    nextData = new float[curr.data.length];

                    // place gathered chunks
                    for (int p = 0; p < size; p++) {
                        int startRow = p * rowsPer;
                        if (rowsPer == 0) break; // nothing to place
                        int offFull = startRow * curr.width * curr.channels;
                        int offG    = p * blockCount;
                        System.arraycopy(gathered, offG, nextData, offFull, blockCount);
                    }

                    // leftover rows (if H not divisible by size) computed on ROOT
                    int leftoverStart = size * rowsPer;
                    if (leftoverStart < curr.height) {
                        if (code == 1) {
                            mirrorRows(curr, nextData, leftoverStart, curr.height);
                        } else {
                            float[] ker = kernelFor(code);
                            int kSize = (int)Math.sqrt(ker.length);
                            applyKernelRows(curr, nextData, kSize, ker, leftoverStart, curr.height);
                        }
                    }
                }

                // Broadcast the full result as the next input (Example2 style).  // ref: Example2Broadcast
                if (rank != ROOT) nextData = new float[curr.data.length];
                MPI.COMM_WORLD.Bcast(nextData, 0, nextData.length, MPI.FLOAT, ROOT);
                curr.data = nextData;

                long tEnd = System.currentTimeMillis();
                double localSecs = (tEnd - tStart) / 1000.0;

                // Allreduce to get max time over ranks (Example8AllReduce idea).  // ref: Example8AllReduce
                double[] send = new double[]{localSecs};
                double[] recv = new double[1];
                MPI.COMM_WORLD.Allreduce(send, 0, recv, 0, 1, MPI.DOUBLE, MPI.MAX);
                if (rank == ROOT) {
                    System.out.printf("Step %d (code=%d) max time: %.3f s%n", oi+1, code, recv[0]);
                }
            }

            // Save only on ROOT
            if (rank == ROOT) {
                if (rank == ROOT) {
                 File inF = new File(list.get(fi));
                    String outName = (nFiles == 1)
                            ? ("output_mpi" + size + ".ppm")
                            : ("output_" + inF.getName());

                    PpmImage saveImg = new PpmImage(curr.width, curr.height, curr.channels, curr.depth);
                    System.arraycopy(curr.data, 0, saveImg.data, 0, curr.data.length);

                    File outFile = new File(outDirPath, outName);
                    saveImg.ppmExport(outFile.getAbsolutePath());
                    System.out.println("Saved " + outFile.getAbsolutePath());
}
            }
        }

        if (rank == ROOT) {
            long t1All = System.currentTimeMillis();
            System.out.printf("MPI total elapsed: %.3f s%n", (t1All - t0All) / 1000.0);
        }
        MPI.Finalize(); // Example1 finish.  // ref: Example1Init
    }
}
