package fr.gael.drb.cortex.topic.sentinel3.jai.operator;

import javax.media.jai.JAI;
import javax.media.jai.OperationDescriptorImpl;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.registry.RenderedRegistryMode;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

/**
 * Created by fmarino on 31/05/2017.
 */
public class QuicklookVgpDescriptor extends OperationDescriptorImpl
{
    public final static String OPERATION_NAME = "QuicklookVgp";

    /**
     * The resource strings that provide the general documentation and
     * specify the parameter list for the "Syn VGP" operation.
     */
    protected static String[][] resources =
            {
                    { "GlobalName", OPERATION_NAME },
                    { "LocalName", OPERATION_NAME },
                    { "Vendor", "fr.gael.drb.cortex.topic.sentinel3.jai.operator" },
                    { "Description", "Performs the rendering of S3 Syn Vegetation dataset." },
                    { "DocURL", "http://www.gael.fr/drb" },
                    { "Version", "1.0" },
                    { "arg0Desc", "pixels correction"}
            };

    /**
     * Modes supported by this operator.
     */
    private static String[] supportedModes = { "rendered" };

    /**
     * The parameter names for the "QuicklookOlci" operation..
     */
    private static String[] paramNames = { "pixels_correction"};

    /**
     * The parameter class types for the "QuicklookOlci" operation.
     */
    private static Class<?>[] paramClasses = { Common.PixelCorrection[].class };

    /**
     * The parameter default values for the "QuicklookOlci" operation..
     */
    private static Object[] paramDefault={null};

    public QuicklookVgpDescriptor()
    {
        super(resources, supportedModes, 4, paramNames, paramClasses,
                null, null);
    }

    /**
     * Create the Render Operator to compute Olci quicklook.
     *
     * <p>Creates a <code>ParameterBlockJAI</code> from all
     * supplied arguments except <code>hints</code> and invokes
     * {@link JAI#create(String, ParameterBlock, RenderingHints)}.
     *
     * @see JAI
     * @see ParameterBlockJAI
     * @see RenderedOp
     *
     * @param bands RenderedImage
     * @param pixels_correction per bands scale/offset pixels correction
     * @return The <code>RenderedOp</code> destination.
     * @throws IllegalArgumentException if sources is null.
     * @throws IllegalArgumentException if a source is null.
     */
    public static RenderedOp create(Common.PixelCorrection[] pixels_correction,
                                    RenderingHints hints, RenderedImage... sources)
    {
        ParameterBlockJAI pb =
                new ParameterBlockJAI(OPERATION_NAME,
                        RenderedRegistryMode.MODE_NAME);

        int numSources = sources.length;
        // Check on the source number
        if (numSources <= 0)
        {
            throw new IllegalArgumentException("No resources are present");
        }

        // Setting of all the sources
        for (int index = 0; index < numSources; index++)
        {
            RenderedImage source = sources[index];
            if (source == null)
            {
                throw new IllegalArgumentException("This resource is null");
            }
            pb.setSource(source, index);
        }
      /*To Be remove */
        pb.setParameter(paramNames[0], pixels_correction);

        return JAI.create(OPERATION_NAME, pb, hints);
    } //create
}
