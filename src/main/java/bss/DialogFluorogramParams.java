package bss;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.TextField;

import ij.IJ;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;

public class DialogFluorogramParams implements DialogListener
{
	final GenericDialog gdHist = new GenericDialog( "Build cytofluorogram" );
	
	final FGParameters fgParams;
	
	Checkbox cAxesNames;
	TextField sCh1;
	TextField sCh2;
	
	public DialogFluorogramParams(final FGParameters fgParams)
	{
		this.fgParams = fgParams;
	}
	
	boolean showDialog(int nChannels)
	{
		final String [] sChannels = new String[nChannels];
		
		for(int i = 0; i < nChannels; i++)
		{
			sChannels[i] = "channel " + Integer.toString( i + 1 );
		}
		final String [] sMapping = new String[] {"Linear", "Log"};
		gdHist.addChoice( "For_X-axis use ", sChannels, sChannels[ 0 ] );
		gdHist.addChoice( "For_Y-axis use ", sChannels, sChannels[ 1 ] );
		gdHist.addCheckbox( "Invert_Y-axis ", BSSsettings.bInvertY );
		gdHist.addChoice( "Axis_mapping ", sMapping, sMapping[BSSsettings.nMapFunction] );
		
		gdHist.addNumericField( "Bins_number_X ", BSSsettings.nBinsX, 0);
		gdHist.addNumericField( "Bins_number_Y ", BSSsettings.nBinsY, 0);

		gdHist.addMessage( "Intensity ranges" );
		gdHist.addMessage( "Intensity range X ch " );
		gdHist.addNumericField("MinX ", BSSsettings.dMinX);
		gdHist.addToSameRow();
		gdHist.addNumericField("MaxX ", BSSsettings.dMaxX);
		gdHist.addMessage( "Intensity range Y ch" );
		gdHist.addNumericField("MinY ", BSSsettings.dMinY);
		gdHist.addToSameRow();
		gdHist.addNumericField("MaxY ", BSSsettings.dMaxY);
		gdHist.addCheckbox( "Add axes names ", BSSsettings.bHasAxesNames );
		gdHist.addStringField( "X_", BSSsettings.sChannelX );
		gdHist.addToSameRow();
		gdHist.addStringField( "Y_", BSSsettings.sChannelY );
		cAxesNames = ( Checkbox ) gdHist.getCheckboxes().get( 1 );
		sCh1 = ( TextField ) gdHist.getStringFields().get( 0 );
		sCh2 = ( TextField ) gdHist.getStringFields().get( 1 );
		gdHist.addDialogListener( this );
		updateDialog();
		gdHist.pack();
		gdHist.showDialog();
		
		if ( gdHist.wasCanceled() )
			return false;
		
		return true;
	}
	
	void updateDialog()
	{
		boolean bEnabled = cAxesNames.getState();
		sCh1.setEnabled( bEnabled );
		sCh2.setEnabled( bEnabled );
	}
	
	@Override
	public boolean dialogItemChanged( GenericDialog gd, AWTEvent e )
	{		
		if(e != null)
		{
			updateDialog();
		}
		if(gdHist.wasOKed())
		{
			readDialogParameters();
		}
		return true;
	}
	
	void readDialogParameters()
	{
		
		fgParams.nChannelX = gdHist.getNextChoiceIndex();
		fgParams.nChannelY = gdHist.getNextChoiceIndex();
		if(fgParams.nChannelX  == fgParams.nChannelY)
		{
			IJ.log("Warning! Channel X axis is equal to Channel Y!");
		}
		
		fgParams.bFlipY = gdHist.getNextBoolean();
		BSSsettings.bInvertY = fgParams.bFlipY;
		Prefs.set("BSS.bInvertY", fgParams.bFlipY);
		
		fgParams.nMapFunction = gdHist.getNextChoiceIndex();		
		BSSsettings.nMapFunction = fgParams.nMapFunction;
		Prefs.set("BSS.nMapFunction", fgParams.nMapFunction);
		
		fgParams.nBinsX = (int)gdHist.getNextNumber();
		BSSsettings.nBinsX = fgParams.nBinsX;
		Prefs.set("BSS.nBinsX", fgParams.nBinsX);
		
		fgParams.nBinsY = (int)gdHist.getNextNumber();
		BSSsettings.nBinsY = fgParams.nBinsY;
		Prefs.set("BSS.nBinsY", fgParams.nBinsY);
		
		fgParams.minmax1[0] = gdHist.getNextNumber();
		BSSsettings.dMinX = fgParams.minmax1[0];
		Prefs.set("BSS.dMinX", BSSsettings.dMinX);
		
		fgParams.minmax1[1] = gdHist.getNextNumber();
		BSSsettings.dMaxX = fgParams.minmax1[1];
		Prefs.set("BSS.dMaxX", BSSsettings.dMaxX);
		
		fgParams.minmax2[0] = gdHist.getNextNumber();
		BSSsettings.dMinY = fgParams.minmax2[0];
		Prefs.set("BSS.dMinY", BSSsettings.dMinY);
		
		fgParams.minmax2[1] = gdHist.getNextNumber();
		BSSsettings.dMaxY = fgParams.minmax2[1];
		Prefs.set("BSS.dMaxY", BSSsettings.dMaxY);
				
		fgParams.bHasAxesNames = gdHist.getNextBoolean();
		BSSsettings.bHasAxesNames = fgParams.bHasAxesNames;
		Prefs.set("BSS.bHasAxesNames", fgParams.bHasAxesNames);
		
		if(fgParams.bHasAxesNames)
		{
			fgParams.sChannelX = gdHist.getNextString();
			BSSsettings.sChannelX = fgParams.sChannelX;
			Prefs.set("BSS.sChannelX", fgParams.sChannelX);
			
			fgParams.sChannelY = gdHist.getNextString();
			BSSsettings.sChannelY = fgParams.sChannelY;
			Prefs.set("BSS.sChannelY", fgParams.sChannelY);
		}
	}

}
