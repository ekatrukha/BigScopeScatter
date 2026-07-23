package bss;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.function.DoubleUnaryOperator;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.process.ImageProcessor;
import bss.io.SpimDataLoader;
import mpicbg.spim.data.generic.AbstractSpimData;

public class CFGParameters
{
	public String sDataPath = "";
	public String sDataFilename = "";
	public int nChannel1;
	public int nChannel2;
	public boolean bFlipY = false;
	public int nMapFunction = 0;
	public static final int BSS_Linear = 0, BSS_Log = 1; 
	public int nBinsX = 512;
	public int nBinsY = 512;
	public final double [] minmax1 = new double [2];
	public final double [] minmax2 = new double [2];
	
	public String getFullDataPathFilename()
	{
		return sDataPath +"/" + sDataFilename;
	}
	
	public DoubleUnaryOperator getMapFunction()
	{
		if ( nMapFunction == BSS_Linear)
		{
			return  x -> x;
		}
		return  x -> Math.log(x);
	}
	
	public DoubleUnaryOperator getInverseMapFunction()
	{
		if ( nMapFunction == BSS_Linear)
		{
			return  x -> x;
		}
		return  x -> Math.exp(x);
	}
	
	public void printParams()
	{
		IJ.log( "Data path " + sDataPath );
		IJ.log( "Data filename " + sDataFilename );
		IJ.log( "Axis X channel number " + nChannel1 );
		IJ.log( "Axis Y channel number " + nChannel2 );
		IJ.log( "Invert Y axis " + bFlipY );
		switch (nMapFunction)
		{
		case BSS_Linear:
			IJ.log( "Mapping function " + "linear" );
			break;
		case BSS_Log:
			IJ.log( "Mapping function " + "log" );
			break;
		}
		IJ.log( "Bins X axis " + nBinsX );
		IJ.log( "Bins Y axis " + nBinsY );
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		IJ.log("Intensity range axis X, min " + df.format( minmax1[0] ) + " max " + df.format( minmax1[1] ));
		IJ.log("Intensity range axis Y, min " + df.format( minmax2[0] ) + " max " + df.format( minmax2[1] ));
		
	}
	

	/**
	 * Encodes parameters as key-value properties inside the ImagePlus.
	 */
	public void saveToImagePlus(final ImagePlus imp) 
	{
	    if (imp == null) return;
	    
	    imp.setProp("CFG_sVersion", GlobalParameters.sVersion);
	    imp.setProp("CFG_sDataPath", sDataPath);
	    imp.setProp("CFG_sDataFilename", sDataFilename);
	    imp.setProp("CFG_nChannel1", String.valueOf(nChannel1));
	    imp.setProp("CFG_nChannel2", String.valueOf(nChannel2));
	    imp.setProp("CFG_bFlipY", String.valueOf(bFlipY));
	    imp.setProp("CFG_nMapFunction", String.valueOf(nMapFunction));
	    imp.setProp("CFG_nBinsX", String.valueOf(nBinsX));
	    imp.setProp("CFG_nBinsY", String.valueOf(nBinsY));
	    imp.setProp("CFG_minmax1_0", String.valueOf(minmax1[0]));
	    imp.setProp("CFG_minmax1_1", String.valueOf(minmax1[1]));
	    imp.setProp("CFG_minmax2_0", String.valueOf(minmax2[0]));
	    imp.setProp("CFG_minmax2_1", String.valueOf(minmax2[1]));
	}

	/**
	 * Decodes and restores parameters from ImagePlus properties.
	 */
	public boolean loadFromImagePlus(final ImagePlus imp) 
	{
		if (imp == null) 
			return false;
		String sVersion = "";
		boolean bParseOk = true;
		String valS;
		double valD;
		if ((valS = imp.getProp("CFG_sVersion")) != null) 
			sVersion  = valS;
		else
			bParseOk = false;
		if ((valS = imp.getProp("CFG_sDataPath")) != null) 
			sDataPath  = valS;
		else
			bParseOk = false;	

		if ((valS = imp.getProp("CFG_sDataFilename")) != null) 
			sDataFilename  = valS;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("CFG_nChannel1")) != Double.NaN) 
			nChannel1  = (int)valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("CFG_nChannel2")) != Double.NaN) 
			nChannel2  = (int)valD;
		else
			bParseOk = false;

		if ((valS = imp.getProp("CFG_bFlipY")) != null) 
			bFlipY  = Boolean.parseBoolean( valS);
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("CFG_nMapFunction")) != Double.NaN) 
			nMapFunction  = (int)valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("CFG_nBinsX")) != Double.NaN) 
			nBinsX  = (int)valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("CFG_nBinsY")) != Double.NaN) 
			nBinsY  = (int)valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("CFG_minmax1_0")) != Double.NaN) 
			minmax1[0]  = valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("CFG_minmax1_1")) != Double.NaN) 
			minmax1[1]  = valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("CFG_minmax2_0")) != Double.NaN) 
			minmax2[0]  = valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("CFG_minmax2_1")) != Double.NaN) 
			minmax2[1]  = valD;
		else
			bParseOk = false;

		if(!bParseOk)
		{
			IJ.error("BigScopeScatter error", "Error loading stored cytofluorogram parameters for " + imp.getTitle() + " image!\n"
					+ "Probably the image was not generated by the plugin");
			return false;
		}
		if(!sVersion.equals( GlobalParameters.sVersion ))
		{
			IJ.log( "Warining! The plugin version of cytofluorogram "+sVersion 
					+ " is not equal to the current " +GlobalParameters.sVersion);
			IJ.log( "It should be fine, in principle, loading parameters anyway." );
		}
		return true;
	}
	
	public static void applyHistParameters(final ImagePlus imp, final CFGParameters histParams)
	{
		
		final DoubleUnaryOperator finv = histParams.getInverseMapFunction();
		final DoubleUnaryOperator f = histParams.getMapFunction();
		
		final double min1 = f.applyAsDouble( histParams.minmax1[0] );
		final double max1 = f.applyAsDouble( histParams.minmax1[1] );
		final double min2 = f.applyAsDouble( histParams.minmax2[0] );
		final double max2 = f.applyAsDouble( histParams.minmax2[1] );
		final double binWx = (max1 - min1) / histParams.nBinsX;
    	final double binWy = (max2 - min2) / histParams.nBinsY;	
		final ImageCanvas canvas = imp.getCanvas();
		final ImageProcessor ip = imp.getProcessor();
		
		final int nHeight = ip.getHeight() - 1;
		canvas.addMouseMotionListener(new MouseMotionAdapter() {
		    @Override
		    public void mouseMoved(MouseEvent e) {
		        int x = canvas.offScreenX(e.getX());
		        int y = canvas.offScreenY(e.getY());
		        
		        //get the value of the counts
		        float fCount = 0.0f;
		        try {
		        	fCount = ip.getf( x, y );
		        }
		        catch(Exception exc)
		        {
		        	IJ.log("error");
		        }
		        //map to the cytofluorogram
		        if(histParams.bFlipY)
		        {
		        	y = nHeight - y;
		        }
		        final double myX = finv.applyAsDouble( min1 + (x + 0.5) * binWx);//,10);
		        final double myY = finv.applyAsDouble( min2 + (y + 0.5) * binWy);//,10);

		        IJ.showStatus(
		            String.format("Count %.0f, Int1=%.2f (%d), Int2=%.2f (%d)", fCount, myX, x, myY, y)
		        );
		    }
		});	
	}
	
	public static AbstractSpimData< ? > getDataFromFilename(final String sPathFilenameIni, final CFGParameters cfgParamsFile)
	{

		final File f = new File(sPathFilenameIni);
		cfgParamsFile.sDataFilename = f.getName();
		cfgParamsFile.sDataPath = f.getParent();
		boolean bXML = false;
		
		if(cfgParamsFile.sDataFilename.endsWith( "xml" ) || cfgParamsFile.sDataFilename.endsWith( "h5" ))
		{
			bXML = true;
			if(cfgParamsFile.sDataFilename.endsWith( "h5" ))
			{
				String sFilenameh5 = cfgParamsFile.sDataFilename;
				cfgParamsFile.sDataFilename = cfgParamsFile.sDataFilename.substring( 0, cfgParamsFile.sDataFilename.length() - 2 );
				cfgParamsFile.sDataFilename = cfgParamsFile.sDataFilename + "xml";
				IJ.log( "Opening " + cfgParamsFile.sDataFilename + " instead of " + sFilenameh5 + ".");
			}
		}
		if(bXML)
		{
			return SpimDataLoader.loadHDF5(cfgParamsFile.getFullDataPathFilename());
		}
		return SpimDataLoader.loadBioFormats( cfgParamsFile.getFullDataPathFilename());
	}

}
