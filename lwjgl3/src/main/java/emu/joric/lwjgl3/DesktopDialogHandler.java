package emu.joric.lwjgl3;

import java.awt.Image;
import java.awt.Window;
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
import javax.swing.JFrame;
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

    // The Swing dialogs are shown on the AWT event dispatch thread (see the
    // comment in confirm() for why), which is not the libGDX/GLFW main thread
    // that owns the application window. On Windows the OS will then open a
    // dialog *behind* the application window, because a thread that does not
    // own the foreground window is not allowed to bring a new window to the
    // front. To work around this each dialog is given a temporary owner window
    // (see createDialogOwner) that is brought to the foreground.
    //
    // When true, that owner is made always-on-top, which reliably foregrounds
    // the dialog but also keeps it pinned above every other window for as long
    // as it is open. When false (the default), a gentler toFront()/requestFocus()
    // is used instead, which does not pin the dialog above other applications
    // (nicer if e.g. you want to switch away to check a filename) but may be
    // less reliable at foregrounding on some platforms. Flip this to true if
    // dialogs still appear behind the application window.
    private static final boolean DIALOG_ALWAYS_ON_TOP = false;

    /**
     * Creates a temporary, effectively invisible owner window for a dialog and
     * brings it to the foreground, so the dialog appears in front of the
     * application rather than behind it (see DIALOG_ALWAYS_ON_TOP). The UTILITY
     * window type keeps the owner out of the taskbar and the alt-tab list, and
     * the 1x1 centred size keeps it out of sight while still centring the dialog
     * on screen. The caller must dispose() the returned frame once the dialog
     * has closed. Must be called on the AWT event dispatch thread.
     *
     * @return the temporary owner frame to pass as the dialog's parent.
     */
    private JFrame createDialogOwner() {
        JFrame owner = new JFrame();
        owner.setUndecorated(true);
        owner.setType(Window.Type.UTILITY);
        owner.setSize(1, 1);
        owner.setLocationRelativeTo(null);
        owner.setAlwaysOnTop(DIALOG_ALWAYS_ON_TOP);
        owner.setVisible(true);
        if (!DIALOG_ALWAYS_ON_TOP) {
            owner.toFront();
            owner.requestFocus();
        }
        return owner;
    }

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
                JFrame owner = createDialogOwner();
                final int output;
                try {
                    output = JOptionPane.showConfirmDialog(owner, message, "Please confirm", JOptionPane.YES_NO_OPTION);
                } finally {
                    owner.dispose();
                }
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
                JFrame owner = createDialogOwner();
                final int returnValue;
                try {
                    returnValue = jfc.showOpenDialog(owner);
                } finally {
                    owner.dispose();
                }
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
                JFrame owner = createDialogOwner();
                final String text;
                try {
                    text = (String) JOptionPane.showInputDialog(owner, message, "Please enter value",
                            JOptionPane.INFORMATION_MESSAGE, null, null, initialValue != null ? initialValue : "");
                } finally {
                    owner.dispose();
                }
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
                JFrame owner = createDialogOwner();
                JDialog dialog = pane.createDialog(owner, "About JOric");
                dialog.setIconImage(loadImage("png/joric-32x32.png"));
                try {
                    dialog.show();
                } finally {
                    dialog.dispose();
                    owner.dispose();
                }

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
                JFrame owner = createDialogOwner();
                final Object result;
                try {
                    result = JOptionPane.showInputDialog(owner, message, dialogTitle,
                            JOptionPane.QUESTION_MESSAGE, null, options, currentSelection);
                } finally {
                    owner.dispose();
                }
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
