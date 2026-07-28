package bss;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;

public class Misc
{
	/** wrap a single channel of SpimData to RAI in XYZT format **/
	public static < T extends RealType< T > & NativeType< T > > 
	RandomAccessibleInterval<T> getRAIXYZT(final AbstractSpimData< ? > spimData, final int nChannel)
	{
		List<RandomAccessibleInterval<T>> raiXYZT = new ArrayList<> ();
		final BasicImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();
		final int nTimePoints = spimData.getSequenceDescription().getTimePoints().size();
		for (int nT = 0; nT < nTimePoints; nT++)
		{
			final RandomAccessibleInterval< T > raiTP = Cast.unchecked(imgLoader.getSetupImgLoader(nChannel).getImage(nT));
			if(raiTP.numDimensions() == 3)
			{
				raiXYZT.add( raiTP );
			}
			else
			{
				raiXYZT.add( Views.addDimension( raiTP, 0, 0 ) );
			}
		}
		return Views.stack(raiXYZT);
	}
}
