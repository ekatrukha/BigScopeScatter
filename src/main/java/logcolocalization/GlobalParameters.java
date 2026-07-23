package logcolocalization;

import ij.Prefs;

public class GlobalParameters
{
	
	public static String sVersion = "0.0.1";
	public static String lastDir = Prefs.get( "LC.lastDir", "" );
	public static boolean bInvertY = Prefs.get( "LC.bInvertY", true );
	public static int nBinsX = (int)Prefs.get( "LC.nBinsX", 512);
	public static int nBinsY = (int)Prefs.get( "LC.nBinsY", 512);
	public static int nMapFunction = (int)Prefs.get( "LC.nMapFunction", CFGParameters.LC_Log);
	public static double dMinX = (int)Prefs.get( "LC.dMinX", 10.);
	public static double dMaxX = (int)Prefs.get( "LC.dMaxX", 65535.);
	public static double dMinY = (int)Prefs.get( "LC.dMinY", 10.);
	public static double dMaxY = (int)Prefs.get( "LC.dMaxY", 65535.);
}
