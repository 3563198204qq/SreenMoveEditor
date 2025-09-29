package com.print;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsActions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.ResourceBundle;

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
        String fileName = file != null ? file.getName() : getMessage("unnamed.file");

        // 计算选区起点对应的逻辑行
        Point editorLocation = currentEditor.getContentComponent().getLocationOnScreen();
        int relX = screenX - editorLocation.x;
        int relY = screenY - editorLocation.y;
        VisualPosition visualPos = currentEditor.xyToVisualPosition(new Point(relX, relY));
        LogicalPosition logicalPos = currentEditor.visualToLogicalPosition(visualPos);

        Window owner = WindowManager.getInstance().getFrame(project);
        JDialog floatingDialog = new JDialog(owner) {
            @Override
            protected JRootPane createRootPane() {
                JRootPane rootPane = new JRootPane() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2d = (Graphics2D) g.create();
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        
                        // 绘制圆角背景
                        g2d.setColor(new Color(45, 45, 45));
                        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                        
                        // 绘制圆角边框
                        g2d.setColor(new Color(100, 100, 100));
                        g2d.setStroke(new BasicStroke(1.0f));
                        g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                        
                        g2d.dispose();
                    }
                };
                rootPane.setOpaque(false);
                return rootPane;
            }
            
            @Override
            public void setBounds(int x, int y, int width, int height) {
                super.setBounds(x, y, width, height);
                // 设置圆角形状
                setShape(new java.awt.geom.RoundRectangle2D.Double(0, 0, width, height, 8, 8));
                repaint();
            }
        };
        floatingDialog.setUndecorated(true);
        floatingDialog.setBounds(screenX, screenY, Math.max(width, 300), Math.max(height, 200));
        // 不要默认置顶，避免压住 IDEA 的对话框/搜索窗
        floatingDialog.setAlwaysOnTop(false);
        // 让窗口更像工具窗，不打断主窗口的输入焦点
        try { floatingDialog.setType(Window.Type.UTILITY); } catch (Throwable ignore) { }
        floatingDialog.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        floatingDialog.setAutoRequestFocus(false);
        floatingDialog.setLayout(new BorderLayout());

        // 创建圆角标题栏
        JPanel titleBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 绘制圆角背景，顶部圆角
                g2d.setColor(new Color(60, 60, 60));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight() + 4, 8, 8);
                
                // 绘制底部直线，确保与内容区域无缝连接
                g2d.setColor(new Color(60, 60, 60));
                g2d.fillRect(0, getHeight() - 2, getWidth(), 4);
                
                g2d.dispose();
            }
        };
        titleBar.setPreferredSize(new Dimension(0, 25));
        titleBar.setLayout(new BorderLayout());
        
        // 标题标签，添加左边距
        JLabel titleLabel = new JLabel(fileName);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        titleBar.add(titleLabel, BorderLayout.WEST);
        // 创建现代化的关闭按钮
        JButton closeButton = new JButton() {
            private boolean isHovered = false;
            
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int width = getWidth();
                int height = getHeight();
                
                // 绘制背景
                if (getModel().isPressed()) {
                    // 按下状态 - 深红色，圆形背景稍大
                    g2d.setColor(new Color(180, 40, 40));
                    g2d.fillOval(0, 0, width, height);
                } else if (isHovered) {
                    // 悬停状态 - 红色，圆形背景更大
                    g2d.setColor(new Color(220, 50, 50));
                    g2d.fillOval(-1, -1, width + 2, height + 2);
                } else {
                    // 正常状态 - 半透明灰色，正常尺寸
                    g2d.setColor(new Color(80, 80, 80, 100));
                    g2d.fillOval(2, 2, width - 4, height - 4);
                }
                
                // 绘制关闭图标 "×"
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                
                int centerX = width / 2;
                int centerY = height / 2;
                int size = Math.min(width, height) / 4;
                
                // 绘制两条交叉的线
                g2d.drawLine(centerX - size, centerY - size, centerX + size, centerY + size);
                g2d.drawLine(centerX + size, centerY - size, centerX - size, centerY + size);
                
                g2d.dispose();
            }
            
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(18, 18); // 恢复原来的按钮尺寸
            }
            
            // 添加鼠标监听器来检测悬停状态
            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        isHovered = true;
                        repaint();
                    }
                    
                    @Override
                    public void mouseExited(MouseEvent e) {
                        isHovered = false;
                        repaint();
                    }
                });
            }
        };
        
        closeButton.setPreferredSize(new Dimension(18, 18));
        closeButton.setBorder(null);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusPainted(false);
        closeButton.setOpaque(false);

        // 创建关闭按钮容器，添加右边距和上边距
        JPanel closeButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeButtonPanel.setOpaque(false);
        closeButtonPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 8));
        closeButtonPanel.add(closeButton);

        final Editor[] floatingEditor = new Editor[1];
        floatingEditor[0] = EditorFactory.getInstance().createEditor(document, project,file,false);

        // 保持简单的层级策略：浮动框不置顶，由系统正常管理层级

        closeButton.addActionListener(e -> {
            EditorFactory.getInstance().releaseEditor(floatingEditor[0]);
            floatingDialog.dispose();
        });

        titleBar.add(closeButtonPanel, BorderLayout.EAST);

        // 创建圆角内容面板
        JPanel contentPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 绘制圆角背景，底部圆角
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, -4, getWidth(), getHeight() + 4, 8, 8);
                
                g2d.dispose();
            }
        };
        contentPanel.setLayout(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.add(floatingEditor[0].getComponent(), BorderLayout.CENTER);
        
        floatingDialog.add(titleBar, BorderLayout.NORTH);
        floatingDialog.add(contentPanel, BorderLayout.CENTER);

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

                // 如果当前位置在任意滚动条上，则不拦截（交给滚动条处理）
                if (isOnAnyScrollBarAccurate(frame.getContentPane(), frame.getGlassPane(), new Point(x, y))) {
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
                // 默认先恢复普通光标，再根据区域调整
                frame.setCursor(Cursor.getDefaultCursor());
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

                // 判断是否在滚动条上（更精准的坐标换算）
                if (isOnAnyScrollBarAccurate(frame.getContentPane(), frame.getGlassPane(), e.getPoint())) {
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
            public void mouseExited(MouseEvent e) {
                frame.setCursor(Cursor.getDefaultCursor());
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

    // 更精准：从 glassPane 坐标开始，自动换算到目标组件坐标进行命中测试
    private boolean isOnAnyScrollBarAccurate(Component contentRoot, Component glassPane, Point pointOnGlass) {
        if (contentRoot == null || glassPane == null || pointOnGlass == null) return false;
        Point pOnContent = SwingUtilities.convertPoint(glassPane, pointOnGlass, contentRoot);
        return isOnAnyScrollBar(contentRoot, pOnContent);
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

    /**
     * 获取国际化消息
     */
    private String getMessage(String key) {
        try {
            // 使用ResourceBundle加载国际化资源
            ResourceBundle bundle = ResourceBundle.getBundle("messages.plugin", 
                Locale.getDefault(), this.getClass().getClassLoader());
            return bundle.getString(key);
        } catch (Exception e) {
            // 如果国际化失败，返回默认值
            switch (key) {
                case "unnamed.file":
                    return "未命名";
                default:
                    return key;
            }
        }
    }


}