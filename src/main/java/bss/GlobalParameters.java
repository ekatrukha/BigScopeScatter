package bss;

import ij.Prefs;

public class GlobalParameters
{
	
	public static String sVersion = "0.0.1";
	public static String lastDir = Prefs.get( "BSS.lastDir", "" );
	public static boolean bInvertY = Prefs.get( "BSS.bInvertY", true );
	public static int nBinsX = (int)Prefs.get( "BSS.nBinsX", 512);
	public static int nBinsY = (int)Prefs.get( "BSS.nBinsY", 512);
	public static int nMapFunction = (int)Prefs.get( "LC.nMapFunction", CFGParameters.BSS_Log);
	public static double dMinX = (int)Prefs.get( "BSS.dMinX", 10.);
	public static double dMaxX = (int)Prefs.get( "BSS.dMaxX", 65535.);
	public static double dMinY = (int)Prefs.get( "BSS.dMinY", 10.);
	public static double dMaxY = (int)Prefs.get( "BSS.dMaxY", 65535.);
}
