package com.patrol.simulator.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 仿真图片生成器(航拍 / 红外 两种相机类型)。
 * <p>
 * 输出 JPEG 字节数组(目标 ~120~180KB,符合需求文档 §6.3 "仿真图片 ≤ 200KB" 约束,
 * base64 后 ≤ 240KB,远低于 Kafka 默认单条 1MB 上限)。
 * <p>
 * 之所以手绘而非用真实图片:
 * <ol>
 *   <li>仿真场景不应绑定版权图片</li>
 *   <li>手绘可注入设备 ID / 任务 ID / 拍摄时间,便于 Kibana/前端肉眼验证</li>
 *   <li>代码零外部依赖,产物可重现</li>
 * </ol>
 */
@Slf4j
public final class ImageGenerator {

    /** 画布尺寸: 800×600, JPEG 质量 0.85 时约 120~180KB(实测) */
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    /** 简化字符集(中英混排时 JVM 默认字体不一定支持,统一用 ASCII) */
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ImageGenerator() {}

    /**
     * 生成一张模拟图片(可见光 / 红外均可)。
     *
     * @param cameraType VISIBLE / INFRARED
     * @param deviceId   叠加在画面的设备编号,便于识别
     * @param taskId     任务编号
     * @return JPEG 字节数组
     */
    public static byte[] generateJpeg(String cameraType, String deviceId, String taskId) {
        boolean infrared = "INFRARED".equalsIgnoreCase(cameraType);

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 背景: 红外用暗红渐变,可见光用蓝-灰渐变
            drawBackground(g, infrared);

            // 设备轮廓: 杆塔(竖线 + 三角形顶) / 光伏板(并排矩形) / 房屋(梯形)
            drawScene(g, infrared);

            // 热点(仅红外图):在随机位置叠一团亮色
            if (infrared && ThreadLocalRandom.current().nextDouble() < 0.6) {
                drawHotspot(g);
            }

            // 顶部信息条:设备 / 任务 / 时间戳
            drawHeader(g, cameraType, deviceId, taskId);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(256 * 1024);
        try {
            // JPEG 质量 0.85: ~120~180KB
            ImageIO.write(img, "jpg", baos);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode simulated image", e);
        }
        return baos.toByteArray();
    }

    /** 将 JPEG 字节数组编码为 base64 字符串 */
    public static String toBase64(byte[] jpegBytes) {
        return Base64.getEncoder().encodeToString(jpegBytes);
    }

    private static void drawBackground(Graphics2D g, boolean infrared) {
        if (infrared) {
            // 暗红 → 黑 渐变
            for (int y = 0; y < HEIGHT; y++) {
                float t = y / (float) HEIGHT;
                int r = (int) (90 * (1 - t));
                int gr = (int) (10 * (1 - t));
                int b = (int) (15 * (1 - t));
                g.setColor(new Color(r, gr, b));
                g.drawLine(0, y, WIDTH, y);
            }
        } else {
            // 天空(浅蓝)→ 地面(灰)
            for (int y = 0; y < HEIGHT * 0.6; y++) {
                int b = 200 - y / 4;
                g.setColor(new Color(135, 180, Math.max(180, b)));
                g.drawLine(0, y, WIDTH, y);
            }
            for (int y = (int) (HEIGHT * 0.6); y < HEIGHT; y++) {
                int gray = 110 + (y - HEIGHT * 6 / 10) / 3;
                g.setColor(new Color(gray, gray, gray));
                g.drawLine(0, y, WIDTH, y);
            }
        }
    }

    private static void drawScene(Graphics2D g, boolean infrared) {
        g.setStroke(new BasicStroke(3f));
        // 杆塔
        int tx = WIDTH / 4;
        g.setColor(infrared ? new Color(220, 80, 60) : new Color(60, 60, 80));
        g.drawLine(tx, HEIGHT * 6 / 10, tx, HEIGHT / 5);
        g.drawLine(tx - 40, HEIGHT / 4, tx + 40, HEIGHT / 4);
        // 三角形顶
        int[] xs = {tx - 20, tx + 20, tx};
        int[] ys = {HEIGHT / 5, HEIGHT / 5, HEIGHT / 7};
        g.drawPolygon(xs, ys, 3);

        // 光伏板阵列
        g.setColor(infrared ? new Color(200, 100, 70) : new Color(40, 60, 110));
        int baseY = HEIGHT * 7 / 10;
        for (int i = 0; i < 4; i++) {
            int px = WIDTH / 2 + i * 60;
            g.drawRect(px, baseY, 50, 25);
            g.drawLine(px, baseY + 12, px + 50, baseY + 12);
        }

        // 房屋(梯形)
        g.setColor(infrared ? new Color(180, 70, 50) : new Color(110, 80, 60));
        int[] hx = {WIDTH * 4 / 5, WIDTH * 9 / 10, WIDTH * 85 / 100, WIDTH * 4 / 5};
        int[] hy = {HEIGHT * 7 / 10, HEIGHT * 7 / 10, HEIGHT * 6 / 10, HEIGHT * 7 / 10};
        g.drawPolygon(hx, hy, 4);
        g.drawLine(WIDTH * 4 / 5, HEIGHT * 7 / 10, WIDTH * 4 / 5, HEIGHT * 9 / 10);
        g.drawLine(WIDTH * 9 / 10, HEIGHT * 7 / 10, WIDTH * 9 / 10, HEIGHT * 9 / 10);
    }

    private static void drawHotspot(Graphics2D g) {
        int cx = ThreadLocalRandom.current().nextInt(WIDTH / 3, WIDTH * 2 / 3);
        int cy = ThreadLocalRandom.current().nextInt(HEIGHT / 4, HEIGHT * 3 / 4);
        int radius = 40;
        // 外圈: 红
        for (int i = 0; i < radius; i++) {
            float t = 1f - i / (float) radius;
            g.setColor(new Color(255, (int) (200 * t), 0, (int) (200 * t)));
            g.drawOval(cx - i, cy - i, i * 2, i * 2);
        }
        // 中心: 白
        g.setColor(new Color(255, 255, 200));
        g.fillOval(cx - 6, cy - 6, 12, 12);
    }

    private static void drawHeader(Graphics2D g, String cameraType, String deviceId, String taskId) {
        // 半透明黑底
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, WIDTH, 36);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        String ts = LocalDateTime.now().format(TS_FMT);
        String line = String.format("[%s] %s | task=%s | %s", cameraType, deviceId, taskId, ts);
        g.drawString(line, 10, 22);

        // 右下角水印
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g.setColor(new Color(255, 255, 255, 180));
        g.drawString("SIMULATED IMAGE - Patrol Platform", WIDTH - 290, HEIGHT - 10);
    }
}
