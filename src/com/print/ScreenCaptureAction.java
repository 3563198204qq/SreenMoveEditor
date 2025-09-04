package com.print;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * @Author: xiongd
 * @CreateTime: 2025-06-20
 * @Description:
 * @Version: 1.0
 */

public class ScreenCaptureAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
//        javax.swing.JOptionPane.showMessageDialog(null, "Action triggered!");
        Project project = e.getProject();
        if (project == null) return;

        try {
            // 1. 截屏
            BufferedImage screenshot = new Robot().createScreenCapture(
                    new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));

            // 2. 创建全屏遮罩窗口
            JWindow window = new JWindow();
            window.setAlwaysOnTop(true);
            // 增加遮罩的透明度，让用户更容易察觉到截图模式已启动
            window.setBackground(new Color(0, 0, 0, 100)); // 从50增加到100

            // 3. 用于绘制截图和选区
            JPanel panel = new JPanel() {
                Point start = null, end = null;

                {
                    setOpaque(false);
                    addMouseListener(new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent e) {
                            start = e.getPoint();
                            end = start;
                            repaint();
                        }
                        @Override
                        public void mouseReleased(MouseEvent e) {
                            if (start != null && end != null) {
                                int x = Math.min(start.x, end.x);
                                int y = Math.min(start.y, end.y);
                                int w = Math.abs(start.x - end.x);
                                int h = Math.abs(start.y - end.y);

                                // 4. 生成可编辑代码的悬浮框
                                createCodeEditorPanel(project, x, y, w, h);
                            }
                            window.dispose();
                        }
                    });
                    addMouseMotionListener(new MouseMotionAdapter() {
                        @Override
                        public void mouseDragged(MouseEvent e) {
                            end = e.getPoint();
                            repaint();
                        }
                    });
                }

                @Override
                protected void paintComponent(Graphics g) {
                    // 绘制屏幕截图
                    g.drawImage(screenshot, 0, 0, null);
                    
                    // 绘制半透明遮罩层
                    g.setColor(new Color(0, 0, 0, 100));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    
                    // 如果有选区，则绘制选区
                    if (start != null && end != null) {
                        int x = Math.min(start.x, end.x);
                        int y = Math.min(start.y, end.y);
                        int w = Math.abs(start.x - end.x);
                        int h = Math.abs(start.y - end.y);
                        
                        // 清除选区区域的遮罩，显示原始截图
                        g.clearRect(x, y, w, h);
                        // 确保不会超出截图边界
                        int subImageX = Math.max(0, x);
                        int subImageY = Math.max(0, y);
                        int subImageWidth = Math.min(w, screenshot.getWidth() - x);
                        int subImageHeight = Math.min(h, screenshot.getHeight() - y);
                        
                        // 只有当子图像尺寸有效时才绘制
                        if (subImageWidth > 0 && subImageHeight > 0) {
                            g.drawImage(screenshot.getSubimage(
                                subImageX, 
                                subImageY, 
                                subImageWidth, 
                                subImageHeight
                            ), x, y, null);
                        }
                        
                        // 绘制选区边框
                        Graphics2D g2d = (Graphics2D) g.create();
                        g2d.setStroke(new BasicStroke(2));
                        g2d.setColor(Color.BLUE);
                        g2d.drawRect(x, y, w, h);
                        
                        // 绘制选区尺寸提示
                        String sizeText = w + " × " + h;
                        FontMetrics fm = g2d.getFontMetrics();
                        int textWidth = fm.stringWidth(sizeText);
                        int textHeight = fm.getHeight();
                        
                        // 绘制尺寸文本背景
                        g2d.setColor(new Color(0, 0, 0, 180));
                        g2d.fillRect(x, y - textHeight - 5, textWidth + 10, textHeight);
                        
                        // 绘制尺寸文本
                        g2d.setColor(Color.WHITE);
                        g2d.drawString(sizeText, x + 5, y - 5);
                        
                        g2d.dispose();
                    }
                }
            };
            window.setContentPane(panel);
            window.setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
            window.setVisible(true);
            
            // 添加 ESC 键退出功能 - 使用更可靠的方法
            // 创建一个全局键盘监听器
            KeyEventDispatcher dispatcher = new KeyEventDispatcher() {
                @Override
                public boolean dispatchKeyEvent(KeyEvent e) {
                    if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        // 移除事件分发器
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
                        // 关闭窗口
                        window.dispose();
                        return true; // 消费这个事件
                    }
                    return false; // 不消费这个事件
                }
            };
            
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
            
            // 同时也添加组件级别的监听器作为备用
            panel.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        // 移除事件分发器
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
                        window.dispose();
                    }
                }
            });
            
            panel.setFocusable(true);
            panel.requestFocusInWindow();
            
            // 确保窗口也能获得焦点
            window.setFocusable(true);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
    }

    // 生成可编辑代码的悬浮框
    private void createCodeEditorPanel(Project project, int screenX, int screenY, int width, int height) {
        Editor currentEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (currentEditor == null) return;
        Document document = currentEditor.getDocument();

        // 获取当前文件名
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        String fileName = file != null ? file.getName() : "未命名";

        // 计算选区起点对应的逻辑行
        Point editorLocation = currentEditor.getContentComponent().getLocationOnScreen();
        int relX = screenX - editorLocation.x;
        int relY = screenY - editorLocation.y;
        VisualPosition visualPos = currentEditor.xyToVisualPosition(new Point(relX, relY));
        LogicalPosition logicalPos = currentEditor.visualToLogicalPosition(visualPos);

        Window owner = WindowManager.getInstance().getFrame(project);
        JDialog floatingDialog = new JDialog(owner);
        floatingDialog.setUndecorated(true);
        floatingDialog.setBounds(screenX, screenY, Math.max(width, 300), Math.max(height, 200));
        floatingDialog.setAlwaysOnTop(true);
        floatingDialog.setLayout(new BorderLayout());

        JPanel titleBar = new JPanel();
        titleBar.setBackground(new Color(70, 70, 70));
        titleBar.setPreferredSize(new Dimension(0, 25));
        titleBar.setLayout(new BorderLayout());
        JLabel titleLabel = new JLabel(fileName);
        titleLabel.setForeground(Color.WHITE);
        titleBar.add(titleLabel, BorderLayout.WEST);
        JButton closeButton = new JButton("×");
        closeButton.setPreferredSize(new Dimension(25, 25));
        closeButton.setBorder(null);
        closeButton.setBackground(new Color(70, 70, 70));
        closeButton.setForeground(Color.WHITE);

        final Editor[] floatingEditor = new Editor[1];
        floatingEditor[0] = EditorFactory.getInstance().createEditor(document, project,file,false);

        // 添加窗口焦点监听器
        WindowFocusListener focusListener = new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                // 当前窗口获得焦点时保持可见
                floatingDialog.setAlwaysOnTop(true);
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                // 当前窗口失去焦点时，检查是否是 IDEA 主窗口的子窗口获得焦点
                Window oppositeWindow = e.getOppositeWindow();
                if (oppositeWindow != null && isIdeaDialog(oppositeWindow)) {
                    // 如果是 IDEA 对话框获得焦点，则将浮动编辑框置于其后
                    floatingDialog.setAlwaysOnTop(false);
                }
            }
        };

        floatingDialog.addWindowFocusListener(focusListener);

        // 监听 IDEA 主窗口的焦点变化
        attachFocusListener(project, floatingDialog);

        closeButton.addActionListener(e -> {
            EditorFactory.getInstance().releaseEditor(floatingEditor[0]);
            floatingDialog.removeWindowFocusListener(focusListener);
            floatingDialog.dispose();
        });


        titleBar.add(closeButton, BorderLayout.EAST);

        floatingDialog.add(titleBar, BorderLayout.NORTH);
        floatingDialog.add(floatingEditor[0].getComponent(), BorderLayout.CENTER);

        // 添加拖拽和缩放功能
        addDragAndResizeFunctionality(floatingDialog, titleBar, closeButton);

        floatingDialog.setVisible(true);

        // 自动滚动到你框选的那一行
        floatingEditor[0].getScrollingModel().scrollTo(logicalPos, ScrollType.CENTER);

        JScrollPane scrollPane = findScrollPane(floatingEditor[0].getComponent());
        if (scrollPane != null) {
            scrollPane.addMouseWheelListener(new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (e.isControlDown()) {
                        com.intellij.openapi.editor.colors.EditorColorsScheme scheme = floatingEditor[0].getColorsScheme();
                        int oldSize = scheme.getEditorFontSize();
                        int newSize = oldSize - e.getWheelRotation();
                        if (newSize < 8) newSize = 8;
                        if (newSize > 72) newSize = 72;
                        if (newSize != oldSize) {
                            scheme.setEditorFontSize(newSize);
                            floatingEditor[0].getComponent().repaint();
                        }
                        e.consume(); // 只在Ctrl时拦截
                    }
                    // 不按Ctrl时，不拦截，允许正常滚动
                }
            });
        }

    }

    // 添加判断是否为 IDEA 对话框的方法
    private boolean isIdeaDialog(Window window) {
        // 判断窗口是否为 IDEA 的对话框
        return window instanceof JDialog &&
                window.getClass().getName().contains("com.intellij");
    }

    // 添加监听 IDEA 主窗口焦点变化的方法
    private void attachFocusListener(Project project, JDialog floatingDialog) {
        Window ideaFrame = WindowManager.getInstance().getFrame(project);
        if (ideaFrame != null) {
            ideaFrame.addWindowFocusListener(new WindowAdapter() {
                @Override
                public void windowLostFocus(WindowEvent e) {
                    // 当 IDEA 主窗口失去焦点时，检查焦点是否转移到了对话框
                    Window opposite = e.getOppositeWindow();
                    if (opposite instanceof JDialog) {
                        floatingDialog.setAlwaysOnTop(false);
                    }
                }

                @Override
                public void windowGainedFocus(WindowEvent e) {
                    // 当 IDEA 主窗口重新获得焦点时，恢复浮动编辑框的层级
                    floatingDialog.setAlwaysOnTop(true);
                }
            });
        }
    }

    private void addDragAndResizeFunctionality(JDialog frame, JPanel titleBar, JButton closeButton) {
        final int BORDER_THICKNESS = 8;
        final int TITLE_HEIGHT = 25;
        final int CLOSE_BUTTON_WIDTH = 25;

        final Point[] dragStart = new Point[1];
        final Rectangle[] resizeStart = new Rectangle[1];
        final int[] resizeDirection = new int[1];

        // 定义光标数组
        final Cursor[] cursors = {
                Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR),  // 0
                Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR),   // 1
                Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR),  // 2
                Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR),   // 3
                Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR),   // 4
                Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR),  // 5
                Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR),   // 6
                Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)   // 7
        };

        // 创建玻璃面板处理边框事件
        JPanel glassPane = new JPanel() {
            @Override
            public boolean contains(int x, int y) {
                int w = getWidth();
                int h = getHeight();

                // 排除关闭按钮区域
                if (y < TITLE_HEIGHT && x >= w - CLOSE_BUTTON_WIDTH) {
                    return false;
                }

//                // 上边框区域只支持拖拽，不支持缩放
                if (y < TITLE_HEIGHT) {
                    return x >= BORDER_THICKNESS && x < w - BORDER_THICKNESS;
                }

                // 边框区域
                return x < BORDER_THICKNESS || x > w - BORDER_THICKNESS ||
                        y < BORDER_THICKNESS || y > h - BORDER_THICKNESS;
//                return false;

            }
        };

        glassPane.setOpaque(false);
        glassPane.setLayout(null);

        // 鼠标事件处理
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 中键按下时不处理
                if (e.getButton() == MouseEvent.BUTTON2) {
                    return;
                }

                int x = e.getX();
                int y = e.getY();
                int w = frame.getWidth();
                int h = frame.getHeight();

                // 排除关闭按钮区域
                if (y < TITLE_HEIGHT && x >= w - CLOSE_BUTTON_WIDTH) {
                    return;
                }

                // 上边框区域：拖拽
                if (y < TITLE_HEIGHT && x >= BORDER_THICKNESS && x < w - BORDER_THICKNESS) {
                    dragStart[0] = e.getPoint();
                    return;
                }

                // 边框缩放
                boolean left = x < BORDER_THICKNESS;
                boolean right = x > w - BORDER_THICKNESS;
                boolean top = y < BORDER_THICKNESS;
                boolean bottom = y > h - BORDER_THICKNESS;

                if (left || right || top || bottom) {
                    resizeStart[0] = frame.getBounds();
                    dragStart[0] = e.getPoint();

                    // 确定缩放方向
                    if (top && left) resizeDirection[0] = 0;      // NW
                    else if (top && right) resizeDirection[0] = 2; // NE
                    else if (bottom && left) resizeDirection[0] = 5; // SW
                    else if (bottom && right) resizeDirection[0] = 7; // SE
                    else if (top) resizeDirection[0] = 1;         // N
                    else if (bottom) resizeDirection[0] = 6;      // S
                    else if (left) resizeDirection[0] = 3;        // W
                    else if (right) resizeDirection[0] = 4;       // E
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // 中键释放时不处理
                if (e.getButton() == MouseEvent.BUTTON2) {
                    return;
                }

                dragStart[0] = null;
                resizeStart[0] = null;
                resizeDirection[0] = -1;
                frame.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // 中键按下时不改变光标
                if ((e.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) != 0) {
                    frame.setCursor(Cursor.getDefaultCursor());
                    return;
                }

                int x = e.getX();
                int y = e.getY();
                int w = frame.getWidth();
                int h = frame.getHeight();

                // 排除关闭按钮区域
                if (y < TITLE_HEIGHT && x >= w - CLOSE_BUTTON_WIDTH) {
                    frame.setCursor(Cursor.getDefaultCursor());
                    return;
                }

                // 判断是否在滚动条上
                Point mouseOnContent = SwingUtilities.convertPoint(frame.getGlassPane(), e.getPoint(), frame.getContentPane());
                if (isOnAnyScrollBar(frame.getContentPane(), mouseOnContent)) {
                    frame.setCursor(Cursor.getDefaultCursor());
                    return;
                }

                // 上边框区域：拖拽光标
                if (y < TITLE_HEIGHT && x >= BORDER_THICKNESS && x < w - BORDER_THICKNESS) {
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                }

                // 边框缩放光标
                boolean left = x < BORDER_THICKNESS;
                boolean right = x > w - BORDER_THICKNESS;
                boolean top = y < BORDER_THICKNESS;
                boolean bottom = y > h - BORDER_THICKNESS;

                if (top && left) frame.setCursor(cursors[0]);      // NW
                else if (top && right) frame.setCursor(cursors[2]); // NE
                else if (bottom && left) frame.setCursor(cursors[5]); // SW
                else if (bottom && right) frame.setCursor(cursors[7]); // SE
                else if (top) frame.setCursor(cursors[1]);         // N
                else if (bottom) frame.setCursor(cursors[6]);      // S
                else if (left) frame.setCursor(cursors[3]);        // W
                else if (right) frame.setCursor(cursors[4]);       // E
                else frame.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // 中键拖动时不处理
                if ((e.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) != 0) {
                    return;
                }

                if (dragStart[0] == null) return;

                int dx = e.getX() - dragStart[0].x;
                int dy = e.getY() - dragStart[0].y;

                // 拖拽窗口
                if (resizeStart[0] == null) {
                    Point newLocation = frame.getLocation();
                    newLocation.x += dx;
                    newLocation.y += dy;
                    frame.setLocation(newLocation);
                    return;
                }

                // 缩放窗口
                Rectangle newBounds = new Rectangle(resizeStart[0]);

                switch (resizeDirection[0]) {
                    case 0: // NW - 左上角
                        newBounds.x += dx;
                        newBounds.y += dy;
                        newBounds.width -= dx;
                        newBounds.height -= dy;
                        break;
                    case 1: // N - 上边框
                        newBounds.y += dy;
                        newBounds.height -= dy;
                        break;
                    case 2: // NE - 右上角
                        newBounds.y += dy;
                        newBounds.width += dx;
                        newBounds.height -= dy;
                        break;
                    case 3: // W - 左边框
                        newBounds.x += dx;
                        newBounds.width -= dx;
                        break;
                    case 4: // E - 右边框
                        newBounds.width += dx;
                        break;
                    case 5: // SW - 左下角
                        newBounds.x += dx;
                        newBounds.width -= dx;
                        newBounds.height += dy;
                        break;
                    case 6: // S - 下边框
                        newBounds.height += dy;
                        break;
                    case 7: // SE - 右下角
                        newBounds.width += dx;
                        newBounds.height += dy;
                        break;
                }

                // 限制最小尺寸
                if (newBounds.width < 100) newBounds.width = 100;
                if (newBounds.height < 60) newBounds.height = 60;

                frame.setBounds(newBounds);
            }
        };

        glassPane.addMouseListener(mouseAdapter);
        glassPane.addMouseMotionListener(mouseAdapter);

        frame.setGlassPane(glassPane);
        glassPane.setVisible(true);

        // 为关闭按钮设置指针光标
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // 递归查找
    private boolean isOnAnyScrollBar(Component comp, Point mouse) {
        if (comp instanceof JScrollBar) {
            JScrollBar bar = (JScrollBar) comp;
            if (!bar.isVisible()) return false;
            // mouse是相对于父容器的，需要转为bar的本地坐标
            Point mouseInBar = SwingUtilities.convertPoint(comp.getParent(), mouse, bar);
            if (bar.getBounds().contains(mouseInBar)) {
                return true;
            }
        } else if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                Point childPoint = new Point(mouse.x - child.getX(), mouse.y - child.getY());
                if (isOnAnyScrollBar(child, childPoint)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOnScrollBarThumb(Component comp, Point mouse) {
        if (comp instanceof JScrollBar) {
            JScrollBar bar = (JScrollBar) comp;
            if (!bar.isVisible()) return false;
            Rectangle thumb = getScrollBarThumbBounds(bar);
            if (thumb != null) {
                // mouse是相对于父容器的，需要转为bar的本地坐标
                Point mouseInBar = SwingUtilities.convertPoint(comp.getParent(), mouse, bar);
                if (thumb.contains(mouseInBar)) {
                    return true;
                }
            }
        } else if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                Point childPoint = new Point(mouse.x - child.getX(), mouse.y - child.getY());
                if (isOnScrollBarThumb(child, childPoint)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Rectangle getScrollBarThumbBounds(JScrollBar bar) {
        if (bar.getUI() instanceof javax.swing.plaf.basic.BasicScrollBarUI) {
            try {
                java.lang.reflect.Method m = javax.swing.plaf.basic.BasicScrollBarUI.class.getDeclaredMethod("getThumbBounds");
                m.setAccessible(true);
                return (Rectangle) m.invoke(bar.getUI());
            } catch (Exception ex) {
                // ignore
            }
        }
        return null;
    }

    // 工具方法：递归查找 JScrollPane
    private JScrollPane findScrollPane(Component comp) {
        if (comp == null) return null;
        if (comp instanceof JScrollPane) return (JScrollPane) comp;
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                JScrollPane pane = findScrollPane(child);
                if (pane != null) return pane;
            }
        }
        return null;
    }

    // 监听所有IDEA主窗口的最小化/还原/关闭/打开事件
    private void attachMainWindowListener(JDialog floatingDialog) {
        Window[] windows = Window.getWindows();
        for (Window win : windows) {
            if (win instanceof JFrame) {
                ((JFrame) win).addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowIconified(WindowEvent e) {
                        updateFloatingDialogVisibility(floatingDialog);
                    }
                    @Override
                    public void windowDeiconified(WindowEvent e) {
                        updateFloatingDialogVisibility(floatingDialog);
                    }
                    @Override
                    public void windowClosed(WindowEvent e) {
                        updateFloatingDialogVisibility(floatingDialog);
                    }
                    @Override
                    public void windowOpened(WindowEvent e) {
                        updateFloatingDialogVisibility(floatingDialog);
                    }
                });
            }
        }
    }
    // 判断所有主窗口是否都隐藏，并同步悬浮编辑框显示状态
    private void updateFloatingDialogVisibility(JDialog floatingDialog) {
        boolean anyWindowVisible = false;
        Window[] windows = Window.getWindows();
        for (Window win : windows) {
            if (win instanceof JFrame) {
                JFrame frame = (JFrame) win;
                if (frame.isVisible() && frame.getState() != Frame.ICONIFIED) {
                    anyWindowVisible = true;
                    break;
                }
            }
        }
        floatingDialog.setVisible(anyWindowVisible);
    }


}