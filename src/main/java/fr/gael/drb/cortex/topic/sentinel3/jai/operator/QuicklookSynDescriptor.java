/*
 * Data HUb Service (DHuS) - For Space data distribution.
 * Copyright (C) 2013,2014,2015,2016 European Space Agency (ESA)
 * Copyright (C) 2013,2014,2015,2016 GAEL Systems
 * Copyright (C) 2013,2014,2015,2016 Serco Spa
 *
 * This file is part of DHuS software sources.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
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
 * Describes the Syn Quicklook generator operator.
 */
public class QuicklookSynDescriptor extends OperationDescriptorImpl
{
    private static final long serialVersionUID = -8759205585877624076L;

    /**
     * The "QuicklookSyn" operation name.
     */
    public final static String OPERATION_NAME = "QuicklookSyn";

    /**
     * The resource strings that provide the general documentation and
     * specify the parameter list for the "Syn" operation.
     */
    protected static String[][] resources =
            {
                    { "GlobalName", OPERATION_NAME },
                    { "LocalName", OPERATION_NAME },
                    { "Vendor", "fr.gael.drb.cortex.topic.sentinel3.jai.operator" },
                    { "Description", "Performs the rendering of S3 Syn L2 Land dataset." },
                    { "DocURL", "http://www.gael.fr/drb" },
                    { "Version", "1.0" },
                    { "arg0Desc", "pixels correction"},
                    { "arg1Desc", "quality flags"}
            };

    /**
     * Modes supported by this operator.
     */
    private static String[] supportedModes = { "rendered" };

    /**
     * The parameter names for the "QuicklookSyn" operation..
     */
    private static String[] paramNames = { "pixels_correction", "flags" };

    /**
     * The parameter class types for the "QuicklookSyn" operation.
     */
    private static Class<?>[] paramClasses = { Common.PixelCorrection[].class, short[][].class };

    /**
     * The parameter default values for the "QuicklookSyn" operation..
     */
    private static Object[] paramDefault={null, null};

    public QuicklookSynDescriptor()
    {
        super(resources, supportedModes, 1, paramNames, paramClasses,
                null, null);
    }

    /**
     * Create the Render Operator to compute Syn quicklook.
     *
     * <p>Creates a <code>ParameterBlockJAI</code> from all
     * supplied arguments except <code>hints</code> and invokes
     * {@link JAI#create(String, ParameterBlock, RenderingHints)}.
     *
     * @see JAI
     * @see ParameterBlockJAI
     * @see RenderedOp
     *
     * @param band RenderedImage
     * @param pixels_correction per bands scale/offset pixels correction
     * @return The <code>RenderedOp</code> destination.
     * @throws IllegalArgumentException if sources is null.
     * @throws IllegalArgumentException if a source is null.
     */
    public static RenderedOp create(Common.PixelCorrection[]pixels_correction, float[][] flags,
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
        pb.setParameter(paramNames[1], flags);

        return JAI.create(OPERATION_NAME, pb, hints);
    } //create
}
