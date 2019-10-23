
import ij.ImagePlus;
import ij.ImageStack;
import imagescience.feature.Hessian;
import imagescience.image.Coordinates;
import imagescience.image.Dimensions;
import imagescience.image.FloatImage;
import imagescience.image.Image;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Arnold Fertin
 */
public final class FrangiFilter
{
    private FrangiFilter()
    {
    }

    private static FloatImage filter(final Image img,
                                     final double sigma,
                                     final double k,
                                     final double beta,
                                     final boolean smax)
    {
        final Hessian hess = new Hessian();
        Image[] eigens = new Image[2];
        eigens = hess.run(new FloatImage(img), sigma, false).toArray(eigens);

        final Dimensions dims = img.dimensions();
        final double[] S = new double[dims.x * dims.y];
        final Coordinates coords = new Coordinates(0, 0);
        int i;
        for (coords.y = 0, i = 0; coords.y < dims.y; ++coords.y)
        {
            for (coords.x = 0; coords.x < dims.x; ++coords.x, i++)
            {
                final double l1 = eigens[0].get(coords);
                final double l2 = eigens[1].get(coords);
                S[i] = Math.sqrt(l1 * l1 + l2 * l2);
            }
        }
        final double Smax;
        if (smax)
        {
            Smax = new Max().evaluate(S);
        }
        else
        {
            final Percentile perc = new Percentile();
            perc.setData(S);
            final double q1 = perc.evaluate(25);
            final double q3 = perc.evaluate(75);
            Smax = q3 + 1.5d * (q3 - q1);
        }
        final double c = k * Smax;
        final FloatImage vesselness = new FloatImage(dims);
        for (coords.y = 0, i = 0; coords.y < dims.y; ++coords.y)
        {
            for (coords.x = 0; coords.x < dims.x; ++coords.x, i++)
            {
                double l1 = eigens[0].get(coords);
                double l2 = eigens[1].get(coords);
                if (Math.abs(l2) < Math.abs(l1))
                {
                    final double tmp = l2;
                    l2 = l1;
                    l1 = tmp;
                }
                if (l2 > 0)
                {
                    vesselness.set(coords, 0d);
                }
                else
                {
                    final double Rb = l1 / l2;
                    final double v = Math.exp(-Rb * Rb / (2 * beta * beta)) * (1 - Math.
                                                                               exp(-S[i] * S[i] / (2 * c * c)));
                    vesselness.set(coords, v);
                }
            }
        }

        return vesselness;
    }

    public static ImagePlus exec(final ImagePlus imp,
                                 final double sigma,
                                 final double k,
                                 final double beta,
                                 final boolean smax)
    {
        final Image img = Image.wrap(imp);
        return filter(img, sigma, k, beta, smax).imageplus();
    }

    public static ImagePlus exec(final ImagePlus imp,
                                 final double sigma0,
                                 final double sigmaM,
                                 final int qLevel,
                                 final double k,
                                 final double beta,
                                 final boolean smax)
    {
        final double[] scales = createScaleRange(sigma0, sigmaM, qLevel);
        final Image img = Image.wrap(imp);
        final Dimensions dims = new Dimensions(img.dimensions().x, img.dimensions().y, scales.length);
        final FloatImage vessMulti = new FloatImage(dims);
        for (int i = 0; i < scales.length; i++)
        {
            final FloatImage vesselness = filter(img, scales[i], k, beta, smax);
            final Coordinates coords1 = new Coordinates(0, 0);
            final Coordinates coords2 = new Coordinates(0, 0, i);
            for (coords1.y = 0; coords1.y < dims.y; ++coords1.y)
            {
                for (coords1.x = 0; coords1.x < dims.x; ++coords1.x)
                {
                    final double v = vesselness.get(coords1);
                    coords2.x = coords1.x;
                    coords2.y = coords1.y;
                    vessMulti.set(coords2, v);
                }
            }
        }

        final ImagePlus res = vessMulti.imageplus();
        final ImageStack stk = res.getStack();
        for (int i = 0; i < scales.length; i++)
        {
            stk.setSliceLabel("sigma = " + scales[i], i + 1);
        }

        return res;
    }

    private static double width2sigma(final double width)
    {
        return 0.5d + width / (2d * Math.sqrt(3d));
    }

    private static double[] createScaleRange(final double s0,
                                             final double sm,
                                             final int q)
    {
        final int m = ((int) Math.floor(((double) q) * Math.log(sm / s0) / Math.log(2d))) + 1;
        final double[] scales = new double[m];
        for (int i = 0; i < m; i++)
        {
            scales[i] = s0 * Math.pow(2, ((double) i) / q);
        }
        return scales;
    }
}
