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

import fr.gael.drb.DrbNode;
import fr.gael.drb.DrbSequence;
import fr.gael.drb.cortex.topic.sentinel3.jai.operator.Common.PixelCorrection;
import fr.gael.drb.query.Query;
import fr.gael.drb.value.Float;
import fr.gael.drb.value.*;
import fr.gael.drb.value.Integer;
import fr.gael.drb.value.Short;
import fr.gael.drbx.image.DrbCollectionImage;
import fr.gael.drbx.image.DrbImage;
import org.apache.log4j.Logger;

import java.awt.*;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This render image factory is dedicated to the preparation of the OLCI quicklook operator.
 */
public class QuicklookOlciRIF implements RenderedImageFactory
{
   private static final Logger LOGGER = Logger.getLogger(QuicklookOlciRIF.class);

   /**
    * Creates a new instance of <code>QuicklookOlciOpImage</code> in the
    * rendered layer. This operator could be called by chunks of images.
    * A set of additional information are required to compute the pixels
    * adjustment such as sun azimuth/elevation and detectors... The methods to
    * extract these informations are also provided here before.
    *
    * @param param The three R/G/B sources images to be "Merged" together
    *              to produce the Quicklook.
    * @param hints Optionally contains destination image layout.
    */
   @Override
   public RenderedImage create(ParameterBlock param, RenderingHints hints)
   {
      // Get the number of the sources
      int num_sources = param.getNumSources();
      // Creation of a source ArrayList (better than a Vector)
      List<RenderedImage> sources = new ArrayList<>(num_sources);

      // Addition of the sources to the List
      for (int i = 0; i < num_sources; i++)
      {
         sources.add((RenderedImage)param.getSource(i));
      }

      // Extracts parameters
      short[][]  detectors  = (short[][])param.getObjectParameter(0);
      double[][] sza        = (double[][])param.getObjectParameter(1);
      float[][]  solar_flux = (float[][])param.getObjectParameter(2);
      PixelCorrection[]pc   = (PixelCorrection[])param.getObjectParameter(3);
      int[]  bands          = (int[])param.getObjectParameter(4);

      double[][] redReflectance = radianceToReflectance(sources.get(0),pc[0],
            detectors, bands[0], sza, solar_flux);
      double[][] greenReflectance = radianceToReflectance(sources.get(1),pc[1],
            detectors, bands[1], sza, solar_flux);
      double[][] blueReflectance = radianceToReflectance(sources.get(2),pc[2],
            detectors, bands[2], sza, solar_flux);

      InputStream equalizationLUT = getClass().getClassLoader().
            getResourceAsStream("olci-equalization.dat");
      return S3HistogramEqualizer.histogramEqualization(
            redReflectance, greenReflectance, blueReflectance, equalizationLUT);
   }

   /**
    * convert radiance to top of atmosphere reflectance
    *
    * @param band        the raw band data
    * @param pc          pixel correction
    * @param detectors   detector matrix
    * @param band_number the band number
    * @param sza         sun zenith angle
    * @param solar_flux  solar flux
    * @return TOA reflectance defined in [0, 1]
    */
   private double[][] radianceToReflectance(RenderedImage band,
                                            PixelCorrection pc, short[][] detectors, int band_number,
                                            double[][] sza, float[][] solar_flux)
   {
      int width = band.getData().getWidth();
      int height = band.getData().getHeight();
      double[][] out = new double[width][height];

      for (int i = 0; i < height; i++)
      {
         for (int j = 0; j < width; j++)
         {
            int detector = detectors[i][j];
            if (detector >= 0)
            {
               try
               {
                  if (band.getData().getSample(j, i, 0) == pc.nodata)
                  {
                     continue;
                  }

                  double angle = sza[i][j / 64];
                  double radiance = band.getData().getSample(j, i, 0) * pc.scale + pc.offset;
                  double ln = radiance / solar_flux[band_number - 1][detector];
                  double reflectance = Math.PI * ln / Math.cos(Math.toRadians(angle));

                  out[j][i] = Math.max(0, Math.min(1., reflectance));
               }
               catch (Exception e)
               {
                  LOGGER.error("Unable to convert radiance to TOA reflectance for band "
                        + band_number + " due to " + e.getMessage());
               }
            }
         }
      }

      return out;
   }

   /**
    * Retrieve the list of detectors from input sources.
    * @param sources the sentinel-3 OLCI datasources.
    * @return the list of detectors.
    */
   public static short[][] extractDetectors (DrbCollectionImage sources)
   {
      try
      {
         DrbImage image = sources.getChildren().iterator().next();
         DrbNode node = ((DrbNode)(image.getItemSource()));
         
         Query query_rows_number = new Query(
            "instrument_data.nc/root/dimensions/rows");
         Query query_cols_number = new Query(
            "instrument_data.nc/root/dimensions/columns");
         Query query_data = new Query(
            "instrument_data.nc/root/dataset/detector_index/rows/columns");
      
         
         Value vrows = query_rows_number.evaluate(node).getItem(0).getValue().
            convertTo(Value.INTEGER_ID);
         Value vcols = query_cols_number.evaluate(node).getItem(0).getValue().
            convertTo(Value.INTEGER_ID);
         
         int rows = ((Integer)vrows).intValue();
         int cols = ((Integer)vcols).intValue();
      
         DrbSequence sequence = query_data.evaluate(node);
         short[][]ds = new short[rows][cols];
         for (int index_rows=0; index_rows<sequence.getLength(); index_rows++)
         {
            DrbNode row_node = (DrbNode)sequence.getItem(index_rows);
            ValueArray values = (ValueArray)row_node.getValue();
            for(int index_cols=0;index_cols<values.getLength();index_cols++)
            {
               ds[index_rows][index_cols] = 
                 ((Short)values.getElement(index_cols).getValue()).shortValue();
            }
         }
         return ds;
      }
      catch (Exception e)
      {
         LOGGER.error("detector extraction failure.", e);
      }
      return null;
   }
   
   /**
    * Extracts the solar flux from input sentinel-3 OLCI datasources.
    * @param sources the sentinel-3 OLCI datasources.
    * @return the list of solar flux.
    */
   public static float[][] extractSolarFlux (DrbCollectionImage sources)
   {
      try
      {
         DrbImage image = sources.getChildren().iterator().next();
         DrbNode node = ((DrbNode)(image.getItemSource()));
         
         Query query_rows_number = new Query(
            "instrument_data.nc/root/dimensions/bands");
         Query query_cols_number = new Query(
            "instrument_data.nc/root/dimensions/detectors");
         Query query_data = new Query(
            "instrument_data.nc/root/dataset/solar_flux/bands/detectors");
      
         
         Value vrows = query_rows_number.evaluate(node).getItem(0).getValue().
            convertTo(Value.INTEGER_ID);
         Value vcols = query_cols_number.evaluate(node).getItem(0).getValue().
            convertTo(Value.INTEGER_ID);
         
         int rows = ((Integer)vrows).intValue();
         int cols = ((Integer)vcols).intValue();
      
         DrbSequence sequence = query_data.evaluate(node);
         float[][]ds = new float[rows][cols];
         for (int index_rows=0; index_rows<sequence.getLength(); index_rows++)
         {
            DrbNode row_node = (DrbNode)sequence.getItem(index_rows);
            ValueArray values = (ValueArray)row_node.getValue();
            for(int index_cols=0;index_cols<values.getLength();index_cols++)
            {
               ds[index_rows][index_cols] = 
                 ((Float)values.getElement(index_cols).getValue()).floatValue();
            }
         }
         return ds;
      }
      catch (Exception e)
      {
         LOGGER.error("Solar flux extraction failure.", e);
      }
      return null;
   }

   /**
    * Extracts the sun angles from input sentinel-3 OLCI datasources.
    * @param sources the sentinel-3 OLCI datasources.
    * @return the list of sun angles.
    */
   public static double[][] extractSunZenithAngle (DrbCollectionImage sources)
   {
      try
      {
         DrbImage image = sources.getChildren().iterator().next();
         DrbNode node = ((DrbNode)(image.getItemSource()));
         
         Query query_rows_number = new Query(
            "tie_geometries.nc/root/dimensions/tie_rows");
         Query query_cols_number = new Query(
            "tie_geometries.nc/root/dimensions/tie_columns");
         Query query_data = new Query(
            "tie_geometries.nc/root/dataset/SZA/tie_rows/tie_columns");
      
         Value vrows = query_rows_number.evaluate(node).getItem(0).getValue().
            convertTo(Value.INTEGER_ID);
         Value vcols = query_cols_number.evaluate(node).getItem(0).getValue().
            convertTo(Value.INTEGER_ID);
         
         int rows = ((Integer)vrows).intValue();
         int cols = ((Integer)vcols).intValue();
      
         DrbSequence sequence = query_data.evaluate(node);
         double[][]ds = new double[rows][cols];
         for (int index_rows=0; index_rows<sequence.getLength(); index_rows++)
         {
            DrbNode row_node = (DrbNode)sequence.getItem(index_rows);
            ValueArray values = (ValueArray)row_node.getValue();
            for(int index_cols=0;index_cols<values.getLength();index_cols++)
            {
               Int uint_value = (Int)values.getElement(index_cols).getValue();
               double dbl_value = uint_value.doubleValue() * 1.0E-6;
               
               ds[index_rows][index_cols] = dbl_value;
            }
         }
         return ds;
      }
      catch (Exception e)
      {
         LOGGER.error("Solar flux extraction failure.", e);
      }
      return null;
   }
}
