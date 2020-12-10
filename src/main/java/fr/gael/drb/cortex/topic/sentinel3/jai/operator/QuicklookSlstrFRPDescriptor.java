package fr.gael.drb.cortex.topic.sentinel3.jai.operator;

import javax.media.jai.JAI;
import javax.media.jai.OperationDescriptorImpl;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.registry.RenderedRegistryMode;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.util.ArrayList;

/**
 * Created by Immedia
 */
public class QuicklookSlstrFRPDescriptor extends OperationDescriptorImpl
{
    public final static String OPERATION_NAME = "QuicklookSlstrFRP";


    /**
     * The resource strings that provide the general documentation and
     * specify the parameter list for the "Olci" operation.
     */
    protected static String[][] resources =
            {
                    { "GlobalName", OPERATION_NAME },
                    { "LocalName", OPERATION_NAME },
                    { "Vendor", "fr.gael.drb.cortex.topic.sentinel3.jai.operator" },
                    { "Description", "Performs the rendering of S3 SLSTR L2 FRP dataset." },
                    { "DocURL", "http://www.gael.fr/drb" },
                    { "Version", "1.0" },
                    { "arg0Desc", "quality flags"},
                    { "arg1Desc", "fires"}
            };

    /**
     * Modes supported by this operator.
     */
    private static String[] supportedModes = { "rendered" };

    /**
     * The parameter names for the "QuicklookSlstr" operation..
     */
    private static String[] paramNames = { "flags", "fires" };

    /**
     * The parameter class types for the "QuicklookSlstr" operation.
     */
    private static Class<?>[] paramClasses = { int[][].class, ArrayList.class };

    /**
     * Constructs a new Slstr operator, with the parameters specified in
     * static fields. 3 sources are expected.
     */
    public QuicklookSlstrFRPDescriptor()
    {
        super(resources, supportedModes, 1, paramNames, paramClasses,
                null, null);
    }

    /**
     * Create the Render Operator to compute SLSTR  L2L quicklook.
     *
     * <p>Creates a <code>ParameterBlockJAI</code> from all
     * supplied arguments except <code>hints</code> and invokes
     * {@link JAI#create(String, ParameterBlock, RenderingHints)}.
     *
     * @see JAI
     * @see ParameterBlockJAI
     * @see RenderedOp
     *
     * @param LST the RenderedImage
     * @param flags the RenderedImage
     * @param fires of the dataset
     * @return The <code>RenderedOp</code> destination.
     * @throws IllegalArgumentException if sources is null.
     * @throws IllegalArgumentException if a source is null.
     */
    public static RenderedOp create(int[][] flags, ArrayList<Point> fires,
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
        pb.setParameter(paramNames[0], flags);
        pb.setParameter(paramNames[1], fires);

        return JAI.create(OPERATION_NAME, pb, hints);
    } //create

}
