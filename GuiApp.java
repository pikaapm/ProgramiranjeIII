// The graphical interface 

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

public class GuiApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(GuiApp::createAndShow);
    }

    private static void createAndShow() {
        JFrame f = new JFrame("Kernel Image Processing");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(640, 360);
        f.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField inputField = new JTextField();
        JButton chooseFileBtn = new JButton("Choose File");
        JButton chooseFolderBtn = new JButton("Choose Folder");

        JTextField outputField = new JTextField();
        JButton chooseOutBtn = new JButton("Choose Output");

        JTextField opsField = new JTextField("blur"); // e.g. blur+edge

        String[] modes = {"auto", "sequential", "parallel", "distributed", "custom"};
        JComboBox<String> modeBox = new JComboBox<>(modes);
        JTextField threadsField = new JTextField("8");
        threadsField.setEnabled(false);

        JTextArea log = new JTextArea(6, 20);
        log.setEditable(false);
        JScrollPane scroll = new JScrollPane(log);

        JButton runBtn = new JButton("Run");

        int row = 0;
        // Input row
        c.gridx = 0; c.gridy = row; c.weightx = 0; panel.add(new JLabel("Input (file or folder):"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; panel.add(inputField, c);
        c.gridx = 2; c.gridy = row; c.weightx = 0; panel.add(chooseFileBtn, c);
        c.gridx = 3; c.gridy = row; panel.add(chooseFolderBtn, c);
        row++;

        // Output row
        c.gridx = 0; c.gridy = row; c.weightx = 0; panel.add(new JLabel("Output folder:"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; panel.add(outputField, c);
        c.gridx = 2; c.gridy = row; c.gridwidth = 2; panel.add(chooseOutBtn, c);
        c.gridwidth = 1; row++;

        // Operation row
        c.gridx = 0; c.gridy = row; c.weightx = 0; panel.add(new JLabel("Operations (e.g. blur+edge):"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 3; panel.add(opsField, c);
        c.gridwidth = 1; row++;

        // Mode row
        c.gridx = 0; c.gridy = row; c.weightx = 0; panel.add(new JLabel("Mode:"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 0.5; panel.add(modeBox, c);
        c.gridx = 2; c.gridy = row; c.weightx = 0; panel.add(new JLabel("Threads (custom):"), c);
        c.gridx = 3; c.gridy = row; c.weightx = 0.5; panel.add(threadsField, c);
        row++;

        // Run row
        c.gridx = 0; c.gridy = row; c.weightx = 0; panel.add(new JLabel(""), c);
        c.gridx = 1; c.gridy = row; c.weightx = 0; panel.add(runBtn, c);
        row++;

        // Log row
        c.gridx = 0; c.gridy = row; c.gridwidth = 4; c.weightx = 1; c.weighty = 1; c.fill = GridBagConstraints.BOTH;
        panel.add(scroll, c);

        f.setContentPane(panel);
        f.setVisible(true);

        // Actions
        chooseFileBtn.addActionListener(e -> {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("Choose .ppm file");
            ch.setFileSelectionMode(JFileChooser.FILES_ONLY);
            ch.setFileFilter(new FileNameExtensionFilter("PPM images", "ppm"));
            if (ch.showOpenDialog(f) == JFileChooser.APPROVE_OPTION) {
                inputField.setText(ch.getSelectedFile().getAbsolutePath());
            }
        });

        chooseFolderBtn.addActionListener(e -> {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("Choose input folder");
            ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (ch.showOpenDialog(f) == JFileChooser.APPROVE_OPTION) {
                inputField.setText(ch.getSelectedFile().getAbsolutePath());
            }
        });

        chooseOutBtn.addActionListener(e -> {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("Choose output folder");
            ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (ch.showOpenDialog(f) == JFileChooser.APPROVE_OPTION) {
                outputField.setText(ch.getSelectedFile().getAbsolutePath());
            }
        });

        modeBox.addActionListener(e -> {
            String m = ((String) modeBox.getSelectedItem()).toLowerCase();
            threadsField.setEnabled("custom".equals(m));
        });

        runBtn.addActionListener(e -> {
            String in = inputField.getText().trim();
            String out = outputField.getText().trim();
            String ops = opsField.getText().trim();
            String modeSel = ((String) modeBox.getSelectedItem()).toLowerCase();

            if (in.isEmpty() || out.isEmpty() || ops.isEmpty()) {
                JOptionPane.showMessageDialog(f, "Please fill input, output and operations.");
                return;
            }

            // Ensure output exists
            File outDir = new File(out);
            if (!outDir.exists() && !outDir.mkdirs()) {
                JOptionPane.showMessageDialog(f, "Could not create output folder: " + outDir.getAbsolutePath());
                return;
            }

            java.util.List<String> argList = new java.util.ArrayList<>();
            argList.add(in);

            String outNormalized = out;
            if (!outNormalized.endsWith("/") && !outNormalized.endsWith(File.separator)) {
                outNormalized += File.separator;
            }
            argList.add(outNormalized);

            argList.add(ops);
            if (!"auto".equals(modeSel)) {
                if ("custom".equals(modeSel)) {
                    argList.add(threadsField.getText().trim());
                } else {
                    argList.add(modeSel); 
                }
            }

            String[] builtArgs = argList.toArray(new String[0]);

            runBtn.setEnabled(false);
            log.append("Running...\n");

            new Thread(() -> {
                long t0 = System.currentTimeMillis();
                try {
                    ImageProcessor.main(builtArgs);
                    long t1 = System.currentTimeMillis();
                    SwingUtilities.invokeLater(() -> {
                        log.append("Done. Elapsed: " + ((t1 - t0) / 1000.0) + " s\n");
                        runBtn.setEnabled(true);
                    });
                } catch (InterruptedException ex) {
                    SwingUtilities.invokeLater(() -> {
                        log.append("Interrupted: " + ex.getMessage() + "\n");
                        runBtn.setEnabled(true);
                    });
                } catch (Throwable th) {
                    SwingUtilities.invokeLater(() -> {
                        log.append("Error: " + th + "\n");
                        runBtn.setEnabled(true);
                    });
                }
            }).start();
        });
    }
}
