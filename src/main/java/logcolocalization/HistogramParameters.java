package logcolocalization;

import java.util.function.DoubleUnaryOperator;

public class HistogramParameters
{
	public String sDataPath = "";
	public String sDataFilename = "";
	public int nChannel1;
	public int nChannel2;
	public int nBinsX = 512;
	public int nBinsY = 512;
	public final double [] minmax1 = new double [2];
	public final double [] minmax2 = new double [2];
	
	public static final int LC_Linear = 0, LC_Log = 1; 
	public int nMapFunction = 0;
	public boolean bFlipY = false;
	
	public String getFullDataPathFilename()
	{
		return sDataPath +"/" + sDataFilename;
	}
	
	public DoubleUnaryOperator getMapFunction()
	{
		if ( nMapFunction == LC_Linear)
		{
			return  x -> x;
		}
		return  x -> Math.log(x);
	}
	
	public DoubleUnaryOperator getInverseMapFunction()
	{
		if ( nMapFunction == LC_Linear)
		{
			return  x -> x;
		}
		return  x -> Math.exp(x);
	}

}
