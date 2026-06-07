package emu.joric.lwjgl3;

import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;

import emu.joric.ui.ConfirmResponseHandler;
import emu.joric.ui.DialogHandler;
import emu.joric.ui.OpenFileResponseHandler;
import emu.joric.ui.TextInputResponseHandler;

public class DesktopDialogHandler implements DialogHandler {

    private boolean dialogOpen;
    
    private Icon importIcon;
    private Icon exportIcon;
    private Icon clearIcon;
    private Icon resetIcon;
    
    private BufferedImage toBufferedImage(Pixmap pixmap) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PixmapIO.PNG writer = new PixmapIO.PNG(pixmap.getWidth() * pixmap.getHeight() * 4);
            try {
                writer.setFlipY(false);
                writer.setCompression(Deflater.NO_COMPRESSION);
                writer.write(baos, pixmap);
            } finally {
                writer.dispose();
            }

            return ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
        }
    }
    
    private Image loadImage(String imagePath) {
        try {
            Pixmap iconPixmap = new Pixmap(Gdx.files.internal(imagePath));
            BufferedImage image = toBufferedImage(iconPixmap);
            iconPixmap.dispose();
            return image;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private Icon loadIcon(String iconPath) {
        Image image = loadImage(iconPath);
        if (image != null) {
            return new ImageIcon(image);
        } else {
            return null;
        }
    }
    
    private Icon getImportIcon() {
        if (importIcon == null) {
            importIcon = loadIcon("png/import.png");
        }
        return importIcon;
    }
    
    private Icon getExportIcon() {
        if (exportIcon == null) {
            exportIcon = loadIcon("png/export.png");
        }
        return exportIcon;
    }
    
    private Icon getClearIcon() {
        if (clearIcon == null) {
            clearIcon = loadIcon("png/clear.png");
        }
        return clearIcon;
    }
    
    private Icon getResetIcon() {
        if (resetIcon == null) {
            resetIcon = loadIcon("png/reset.png");
        }
        return resetIcon;
    }
    
    @Override
    public void confirm(final String message, final ConfirmResponseHandler responseHandler) {
        // The Swing dialog must run on the AWT Event Dispatch Thread (EDT),
        // not on the libGDX main thread. The Swing API normally expects its 
        // components to be accessed from the AWT EDT. On macOS the libGDX main 
        // thread is also the AppKit main thread (because of -XstartOnFirstThread),
        // so if we made Swing dialog calls from there they would deadlock against 
        // the EDT. So we dispatch via SwingUtilities.invokeLater, and post the 
        // response back to the libGDX main thread so the response handler interacts 
        // with libGDX state safely.
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                final int output = JOptionPane.showConfirmDialog(null, message, "Please confirm", JOptionPane.YES_NO_OPTION);
                dialogOpen = false;
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        if (output != 0) {
                            responseHandler.no();
                        } else {
                            responseHandler.yes();
                        }
                    }
                });
            }
        });
    }

    @Override
    public void openFileDialog(String title, final String startPath,
            final OpenFileResponseHandler openFileResponseHandler) {
        // See confirm() for why the Swing dialog is dispatched onto the EDT
        // and the response is posted back to the libGDX main thread.
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                JFileChooser jfc = null;
                if (startPath != null) {
                    jfc = new JFileChooser(startPath);
                } else {
                    jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
                }
                jfc.setDialogTitle("Select a tape, disk or ZIP file");
                jfc.setAcceptAllFileFilterUsed(false);
                FileNameExtensionFilter filter = new FileNameExtensionFilter("TAP, DSK or ZIP files", "tap", "dsk", "zip");
                jfc.addChoosableFileFilter(filter);

                dialogOpen = true;
                final int returnValue = jfc.showOpenDialog(null);
                final String selectedPath = (returnValue == JFileChooser.APPROVE_OPTION)
                        ? jfc.getSelectedFile().getPath() : null;
                dialogOpen = false;
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        if (selectedPath != null) {
                            System.out.println(selectedPath);
                            openFileResponseHandler.openFileResult(true, selectedPath, null);
                        } else {
                            openFileResponseHandler.openFileResult(false, null, null);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void promptForTextInput(final String message, final String initialValue,
            final TextInputResponseHandler textInputResponseHandler) {
        // See confirm() for why the Swing dialog is dispatched onto the EDT
        // and the response is posted back to the libGDX main thread.
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                final String text = (String) JOptionPane.showInputDialog(null, message, "Please enter value",
                        JOptionPane.INFORMATION_MESSAGE, null, null, initialValue != null ? initialValue : "");
                dialogOpen = false;
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        if (text != null) {
                            textInputResponseHandler.inputTextResult(true, text);
                        } else {
                            textInputResponseHandler.inputTextResult(false, null);
                        }
                    }
                });
            }
        });
    }
    
    @Override
    public void showAboutDialog(final String aboutMessage, final TextInputResponseHandler textInputResponseHandler) {
        // See confirm() for why the Swing dialog is dispatched onto the EDT
        // and the response is posted back to the libGDX main thread. Unlike
        // the other dialog methods this one wasn't previously wrapped in
        // Gdx.app.postRunnable, but the cause was the same: it ran on
        // whatever thread called it, which is the libGDX main thread from
        // the in-emulator UI.
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;

                JButton spacerButton = new JButton(
                        "                                                                  ");
                spacerButton.setVisible(false);
                final JButton exportButton = new JButton(getExportIcon());
                final JButton importButton = new JButton(getImportIcon());
                final JButton resetButton = new JButton(getResetIcon());
                final JButton clearButton = new JButton(getClearIcon());
                final JButton okButton = new JButton("OK");
                //Object[] options = { exportButton, importButton, resetButton, clearButton, spacerButton, okButton };
                Object[] options = { okButton };

                final JOptionPane pane = new JOptionPane(
                        aboutMessage,
                        JOptionPane.INFORMATION_MESSAGE,
                        JOptionPane.DEFAULT_OPTION,
                        loadIcon("png/joric-64x64.png"),
                        options, okButton);

                MouseAdapter mouseListener = new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        JButton button = (JButton)e.getComponent();
                        if (button == exportButton) {
                            pane.setValue("EXPORT");
                        }
                        else if (button == importButton) {
                            pane.setValue("IMPORT");
                        }
                        else if (button == resetButton) {
                            pane.setValue("RESET");
                        }
                        else if (button == clearButton) {
                            pane.setValue("CLEAR");
                        }
                        else {
                            pane.setValue("OK");
                        }
                    }
                };

                exportButton.addMouseListener(mouseListener);
                importButton.addMouseListener(mouseListener);
                resetButton.addMouseListener(mouseListener);
                clearButton.addMouseListener(mouseListener);
                okButton.addMouseListener(mouseListener);

                pane.setComponentOrientation(JOptionPane.getRootFrame().getComponentOrientation());
                JDialog dialog = pane.createDialog("About JOric");
                dialog.setIconImage(loadImage("png/joric-32x32.png"));
                dialog.show();
                dialog.dispose();

                final Object paneValue = pane.getValue();
                dialogOpen = false;
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        if (paneValue != null) {
                            textInputResponseHandler.inputTextResult(true, (String)paneValue);
                        } else {
                            textInputResponseHandler.inputTextResult(false, null);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void promptForOption(final String title, final String message, final String[] options,
            final String currentSelection, final TextInputResponseHandler textInputResponseHandler) {
        // See confirm() for why the Swing dialog is dispatched onto the EDT
        // and the response is posted back to the libGDX main thread.
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                String dialogTitle = (title != null && !title.isEmpty()) ? title : "JOric";
                final Object result = JOptionPane.showInputDialog(null, message, dialogTitle,
                        JOptionPane.QUESTION_MESSAGE, null, options, currentSelection);
                dialogOpen = false;
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null) {
                            textInputResponseHandler.inputTextResult(true, result.toString());
                        } else {
                            textInputResponseHandler.inputTextResult(false, null);
                        }
                    }
                });
            }
        });
    }

    @Override
    public boolean isDialogOpen() {
        return dialogOpen;
    }
}
