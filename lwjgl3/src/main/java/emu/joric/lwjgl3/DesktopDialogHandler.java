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
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                int output = JOptionPane.showConfirmDialog(null, message, "Please confirm", JOptionPane.YES_NO_OPTION);
                dialogOpen = false;
                if (output != 0) {
                    responseHandler.no();
                } else {
                    responseHandler.yes();
                }
            }
        });
    }

    @Override
    public void openFileDialog(String title, final String startPath,
            final OpenFileResponseHandler openFileResponseHandler) {
        Gdx.app.postRunnable(new Runnable() {

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
                int returnValue = jfc.showOpenDialog(null);
                dialogOpen = false;
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    System.out.println(jfc.getSelectedFile().getPath());
                    openFileResponseHandler.openFileResult(true, jfc.getSelectedFile().getPath(), null);
                } else {
                    openFileResponseHandler.openFileResult(false, null, null);
                }
            }
        });
    }

    @Override
    public void promptForTextInput(final String message, final String initialValue,
            final TextInputResponseHandler textInputResponseHandler) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                String text = (String) JOptionPane.showInputDialog(null, message, "Please enter value",
                        JOptionPane.INFORMATION_MESSAGE, null, null, initialValue != null ? initialValue : "");
                dialogOpen = false;
                
                if (text != null) {
                    textInputResponseHandler.inputTextResult(true, text);
                } else {
                    textInputResponseHandler.inputTextResult(false, null);
                }
            }
        });
    }
    
    @Override
    public void showAboutDialog(String aboutMessage, TextInputResponseHandler textInputResponseHandler) {
        dialogOpen = true;
        
        JButton spacerButton = new JButton(
                "                                                                  ");
        spacerButton.setVisible(false);
        JButton exportButton = new JButton(getExportIcon());
        JButton importButton = new JButton(getImportIcon());
        JButton resetButton = new JButton(getResetIcon());
        JButton clearButton = new JButton(getClearIcon());
        JButton okButton = new JButton("OK");
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
        
        if (pane.getValue() != null) {
            textInputResponseHandler.inputTextResult(true, (String)pane.getValue());
        } else {
            textInputResponseHandler.inputTextResult(false, null);
        }
        
        dialogOpen = false;
    }
    
    @Override
    public boolean isDialogOpen() {
        return dialogOpen;
    }
}
