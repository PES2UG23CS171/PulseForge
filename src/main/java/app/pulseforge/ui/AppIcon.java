package app.pulseforge.ui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

public final class AppIcon {
    private AppIcon() {}

    public static BufferedImage create(int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float inset = size * .055f;
        var bounds = new Ellipse2D.Float(inset, inset, size - inset * 2, size - inset * 2);
        g.setPaint(new GradientPaint(0, 0, new Color(31, 42, 62), size, size, new Color(9, 12, 18)));
        g.fill(bounds);
        g.setStroke(new BasicStroke(size * .025f));
        g.setColor(new Color(73, 225, 185));
        g.draw(bounds);

        float cx = size / 2f;
        float pivotY = size * .25f;
        float ballX = size * .69f;
        float ballY = size * .66f;
        g.setStroke(new BasicStroke(size * .045f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(91, 141, 239));
        g.draw(new Line2D.Float(cx, pivotY, ballX, ballY));
        g.setColor(new Color(246, 249, 251));
        g.fill(new Ellipse2D.Float(cx - size * .045f, pivotY - size * .045f, size * .09f, size * .09f));
        g.setColor(new Color(255, 181, 72));
        g.fill(new Ellipse2D.Float(ballX - size * .09f, ballY - size * .09f, size * .18f, size * .18f));

        g.setColor(new Color(73, 225, 185, 170));
        g.setStroke(new BasicStroke(size * .022f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc((int) (size * .19), (int) (size * .17), (int) (size * .62), (int) (size * .62), 202, 115);
        g.dispose();
        return image;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass the output PNG path");
        ImageIO.write(create(1024), "png", Path.of(args[0]).toFile());
    }
}
