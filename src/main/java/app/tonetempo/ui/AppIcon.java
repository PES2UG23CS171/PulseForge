package app.tonetempo.ui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** The T&T Pro icon: a tuning fork (tone) beside a metronome pendulum (tempo). Mirrors docs/icon.svg. */
public final class AppIcon {
    private AppIcon() {}

    public static BufferedImage create(int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / 1024.0, size / 1024.0);

        var square = new RoundRectangle2D.Double(0, 0, 1024, 1024, 460, 460);
        g.setPaint(new GradientPaint(0, 0, new Color(36, 50, 71), 0, 1024, new Color(12, 17, 25)));
        g.fill(square);
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 26), 0, 563, new Color(255, 255, 255, 0)));
        g.fill(square);

        // Tone: tuning fork
        var fork = new Path2D.Double();
        fork.moveTo(262, 230);
        fork.lineTo(262, 580);
        fork.append(new Arc2D.Double(262, 514, 132, 132, 180, 180, Arc2D.OPEN), true);
        fork.lineTo(394, 230);
        g.setStroke(new BasicStroke(52, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint(0, 230, new Color(123, 230, 207), 0, 800, new Color(62, 201, 173)));
        g.draw(fork);
        g.draw(new Line2D.Double(328, 646, 328, 800));
        g.setColor(new Color(62, 201, 173));
        g.fill(new Ellipse2D.Double(328 - 34, 818 - 34, 68, 68));

        // Tempo: metronome pendulum with its swing
        g.setStroke(new BasicStroke(20, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(73, 225, 185, 140));
        g.draw(new Arc2D.Double(620 - 370, 800 - 370, 740, 740, 110, -46, Arc2D.OPEN));
        g.setStroke(new BasicStroke(44, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(91, 141, 239));
        g.draw(new Line2D.Double(620, 800, 790, 300));
        g.setPaint(new RadialGradientPaint(739 - 80 * .3f, 450 - 80 * .4f, 128, new float[] {0f, 1f},
                new Color[] {new Color(255, 208, 137), new Color(240, 150, 43)}));
        g.fill(new Ellipse2D.Double(739 - 80, 450 - 80, 160, 160));
        g.setColor(new Color(246, 249, 251));
        g.fill(new Ellipse2D.Double(620 - 38, 800 - 38, 76, 76));
        g.setColor(new Color(26, 36, 51));
        g.fill(new Ellipse2D.Double(620 - 14, 800 - 14, 28, 28));
        g.dispose();
        return image;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass the output PNG path");
        ImageIO.write(create(1024), "png", Path.of(args[0]).toFile());
    }
}
