package fr.gael.drb.cortex.topic.sentinel3.jai.operator;

import fr.gael.drb.DrbNode;
import fr.gael.drb.DrbSequence;
import fr.gael.drb.query.Query;
import fr.gael.drb.value.Integer;
import fr.gael.drb.value.Value;
import fr.gael.drb.value.ValueArray;
import fr.gael.drbx.image.DrbCollectionImage;
import fr.gael.drbx.image.DrbImage;
import org.apache.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;
import java.util.TreeMap;

/**
 * Created by fmarino on 31/05/2017.
 */
public class QuicklookVgpRIF implements RenderedImageFactory
{
    private static final Logger LOGGER = Logger.getLogger(QuicklookVgpRIF.class);

    /**
     * This operator could be called by chunks of images.
     * it uses the following composition
     * red: MIR
     * green: 0.5 * (B2 + B3)
     * blue: B0 + 0.1 * MIR
     * @param param The three R/G/B sources images to be "Merged" together
     *              to produce the Quicklook.
     * @param hints Optionally contains destination image layout.
     */
    @Override
    public RenderedImage create(ParameterBlock param, RenderingHints hints)
    {
        Common.PixelCorrection pc = ((Common.PixelCorrection[])param.getObjectParameter(0))[0];


        short[][] flags = (short[][]) param.getObjectParameter(1);

        RenderedImage raw = (RenderedImage) param.getSource(0);


        int width = raw.getData().getWidth();
        int height = raw.getData().getHeight();

        double[][] ndvi = new double[width][height];

        // sanity checks on input data
        if(pc == null)
        {
            throw new IllegalArgumentException("Pixel corrections can't be null");
        }

        for (int i = 0; i < height; i++)
        {
            for (int j = 0; j < width; j++)
            {    
                if (raw.getData().getSample(j, i, 0) != pc.nodata )
                {
                    ndvi[j][i] = raw.getData().getSample(j, i, 0) * pc.scale + pc.offset;        		
                }
            }
        }

        BufferedImage out = new BufferedImage(ndvi.length, ndvi[0].length, BufferedImage.TYPE_3BYTE_BGR);
        TreeMap<Float, Color> colorMap = Common.loadColormap("syn_ndvi_normalized.cpd", -0.548, 0.412);
       
        for (int i = 0; i < height; i++)
        {
            for (int j = 0; j < width; j++)
            {
                if (isLand(flags[i][j]) && raw.getData().getSample(j, i, 0) != pc.nodata)
                {
                    out.setRGB(j, i, Common.colorMap((float) (ndvi[j][i]), colorMap).getRGB());
                }
                else if(!isLand(flags[i][j]))
                {
                    out.getRaster().setPixel(j, i, new int[]{0, 0, 0});
                }
                else
                {
                    out.getRaster().setPixel(j, i, new int[]{255, 255, 255});
                }
            }
        }
        return out;
    }

    private boolean isLand(short mask)
    {
        return (mask & 8) > 0;
    }

    public static short[][] extractFlags(DrbCollectionImage sources)
    {
        try
        {
            DrbImage image = sources.getChildren().iterator().next();
            DrbNode node = ((DrbNode) (image.getItemSource()));

            Query query_rows_number = new Query(
                    "sm.nc/root/dimensions/latitude");
            Query query_cols_number = new Query(
                    "sm.nc/root/dimensions/longitude");
            Query query_data = new Query(
                    "sm.nc/root/dataset/SM/latitude/longitude");

            Value vrows = query_rows_number.evaluate(node).getItem(0).getValue().
                    convertTo(Value.INTEGER_ID);
            Value vcols = query_cols_number.evaluate(node).getItem(0).getValue().
                    convertTo(Value.INTEGER_ID);

            int rows = ((Integer) vrows).intValue();
            int cols = ((Integer) vcols).intValue();

            DrbSequence sequence = query_data.evaluate(node);
            short[][] ds = new short[rows][cols];
            for (int index_rows = 0; index_rows < sequence.getLength(); index_rows++)
            {
                DrbNode row_node = (DrbNode) sequence.getItem(index_rows);
                ValueArray values = (ValueArray) row_node.getValue();
                for (int index_cols = 0; index_cols < values.getLength(); index_cols++)
                {
                    ds[index_rows][index_cols] =
                            ((fr.gael.drb.value.UnsignedByte) values.getElement(index_cols).getValue()
                                    .convertTo(Value.UNSIGNED_BYTE_ID))
                                    .shortValue();
                }
            }
            return ds;
        }
        catch (Exception e)
        {
            LOGGER.error("flags extraction failure.", e);
            return null;
        }
    }
}
