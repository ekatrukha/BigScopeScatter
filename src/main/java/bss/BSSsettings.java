package bss;

import ij.Prefs;

public class BSSsettings
{
	public static String sVersion = "0.0.2";
	public static String lastDir = Prefs.get( "BSS.lastDir", "" );
	public static boolean bInvertY = Prefs.get( "BSS.bInvertY", true );
	public static boolean bHasAxesNames = Prefs.get( "BSS.bHasAxesNames", false );
	public static String sChannelX = Prefs.get( "BSS.sChannelX", "" );
	public static String sChannelY = Prefs.get( "BSS.sChannelY", "" );
	public static int nBinsX = (int)Prefs.get( "BSS.nBinsX", 512);
	public static int nBinsY = (int)Prefs.get( "BSS.nBinsY", 512);
	public static int nMapFunction = (int)Prefs.get( "BSS.nMapFunction", FGParameters.BSS_Log);
	public static double dMinX = (int)Prefs.get( "BSS.dMinX", 10.);
	public static double dMaxX = (int)Prefs.get( "BSS.dMaxX", 65535.);
	public static double dMinY = (int)Prefs.get( "BSS.dMinY", 10.);
	public static double dMaxY = (int)Prefs.get( "BSS.dMaxY", 65535.);
	public static final int BSS_ImageJ = 0, BSS_Tiff = 1; 
	public static int nOutputMode = (int)Prefs.get( "BSS.nOutputMode", BSS_Tiff );
	public static final int BSS_Out_ROI = 0, BSS_Out_ratioXY = 1, BSS_Out_ratioYX = 2;
	public static int nOutputType = (int)Prefs.get( "BSS.nOutputType", BSS_Out_ROI );
}
