package bss;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.TextField;

import bss.io.GetFolderDialog;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;

public class DialogOutputROI implements DialogListener
{
	final GenericDialog gdOutput = new GenericDialog( "Output settings" );
	
	final FGParameters fgParams;
	
	public String sOutputPath = "";
		
	public DialogOutputROI(final FGParameters fgParams)
	{
		this.fgParams = fgParams;
	}
	
	boolean showDialog()
	{
		final String [] sOutputType = new String[] {"Scatter ROIs (2 channels)", "Ratio axes X/Y", "Ratio axes Y/X"};
		final String [] sOutputDisk = new String[] {"show in Fiji", "Save as TIFFs"};

		gdOutput.addChoice( "Image:", sOutputType , sOutputType [ BSSsettings.nOutputType  ] );
		gdOutput.addChoice( "Extract to:", sOutputDisk , sOutputDisk [ BSSsettings.nOutputMode ] );
		if(fgParams.bHasAxesNames)
		{
			gdOutput.addMessage( "Axis X [" + fgParams.sChannelX + "]  Axis Y [" + fgParams.sChannelY +"]" );
		}
		gdOutput.addCheckbox( "Subtract background", BSSsettings.bSubtractBG);
		gdOutput.addNumericField("BG X axis", BSSsettings.fBGX);
		gdOutput.addNumericField("BG Y axis", BSSsettings.fBGY);
		gdOutput.showDialog();
		
		if ( gdOutput.wasCanceled() )
			return false;

		BSSsettings.nOutputType = gdOutput.getNextChoiceIndex();
		Prefs.set( "BSS.nOutputType", BSSsettings.nOutputType );
		
		BSSsettings.nOutputMode = gdOutput.getNextChoiceIndex();
		Prefs.set( "BSS.nOutputMode", BSSsettings.nOutputMode );
		
		BSSsettings.bSubtractBG = gdOutput.getNextBoolean();
		Prefs.set("BSS.bSubtractBG", BSSsettings.bSubtractBG);
		
		if(BSSsettings.bSubtractBG)
		{
			BSSsettings.fBGX = ( float ) gdOutput.getNextNumber();			
			Prefs.set("BSS.fBGX", BSSsettings.fBGX);
			
			BSSsettings.fBGY = ( float ) gdOutput.getNextNumber();			
			Prefs.set("BSS.fBGY", BSSsettings.fBGY);
		}
		
		//ask for the folder
		if(BSSsettings.nOutputMode == BSSsettings.BSS_Tiff)
		{
			sOutputPath = GetFolderDialog.getSelectedFolder("Save TIFFs to folder..", false);
			if (sOutputPath == null)
				return false;
		}
		return true;
	}
	
	@Override
	public boolean dialogItemChanged( GenericDialog gd, AWTEvent e )
	{
		return true;
	}

}
