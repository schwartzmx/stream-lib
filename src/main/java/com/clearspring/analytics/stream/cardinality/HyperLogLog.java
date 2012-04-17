package com.clearspring.analytics.stream.cardinality;

import com.clearspring.analytics.hash.MurmurHash;
import com.clearspring.analytics.util.IBuilder;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.util.IntHyperLogLogCounterArray;

import java.io.*;
import java.nio.ByteBuffer;

import static com.clearspring.analytics.util.Bytes.addByteArray;

public class HyperLogLog extends IntHyperLogLogCounterArray implements ICardinality
{
	private double rsd;
	private long n;
	private long sentinelMask;
	
	// default of 1 million will give us a bucket size of 5
	// fact is bucket size remains 5 until several Nmax > several billion
	// so this is fine default in most cases
	private static final long DEFAULT_NMAX = 1000000;

	public static long[] getBits(byte[] M) throws IOException
	{
		int bitSize = M.length / 8;
		long[] bits = new long[bitSize];
		DataInputStream dis = new DataInputStream(new ByteArrayInputStream(M));
		for (int i = 0; i < bitSize; i++)
		{
			bits[i] = dis.readLong();
		}
		return bits;
	}

	/**
	 * Creates a new HyperLogLog counter
	 *
	 * @param rsd the relative standard deviation.
	 * @param n - the max number of elements we expect to count  
	 */
	public HyperLogLog(final double rsd, final long n)
	{
		super(1, n, rsd);
		this.rsd = rsd;
		this.n = n;
		sentinelMask = 1L << ( 1 << registerSize ) - 2;
	}

	/**
	 * Creates a new HyperLogLog counter
	 *
	 * @param rsd the relative standard deviation.
	 */
	public HyperLogLog(final double rsd)
	{
		this(rsd, DEFAULT_NMAX);
	}

	/**
	 * Creates a new HyperLogLog counter
	 *
	 * @param bits - the long array representing the bit vector to restore
	 * @param rsd - relative standard deviation
	 * @param n - desired Nmax
	 */
	public HyperLogLog(final long[] bits, final double rsd, final long n)
	{
		this(rsd, n);
		this.bitVector[0] = LongArrayBitVector.wrap(bits);
		this.registers[0] = bitVector[0].asLongBigList(registerSize);
	}

	@Override
	/**
	 * Offers a new object to the count array.
	 *
	 * note that this implementation does not honor the modified contract
	 * and always returns true.
	 */
	public boolean offer(Object o)
	{
		long x= MurmurHash.hash(o.toString().getBytes());
		final int j = (int)( x & mMinus1 );
		final int r = Fast.leastSignificantBit(x >>> log2m | sentinelMask);
		final LongBigList l = registers[ 0 >>> counterShift ];
		final long offset = ( ( (long)0 << log2m ) + j ) & CHUNK_MASK;
		l.set( offset, Math.max( r + 1, l.getLong( offset ) ) );
		return true;
	}

	@Override
	public long cardinality()
	{
		return (long)this.count(0);
	}

	@Override
	public int sizeof()
	{
		return m;
	}

	@Override
	public byte[] getBytes() throws IOException
	{

		long[] bits = bitVector[0].bits();
		int bytes = bits.length*8;
		byte[] bArray = new byte[bytes + 20];
		
		addByteArray(bArray, 0, n);
		addByteArray(bArray, 8, rsd);
		addByteArray(bArray, 16, bytes);
		addByteArray(bArray, 20, bits);

		return bArray;
	}



	@Override
	public ICardinality merge(ICardinality... estimators) throws CardinalityMergeException
	{
		if (estimators == null || estimators.length == 0)
		{
			return this;
		}

		ICardinality[] estimatorArray = new HyperLogLog[estimators.length+1];
		for (int i = 0; i < estimators.length; i++)
		{
			estimatorArray[i] = estimators[i];
		}
		estimatorArray[estimatorArray.length-1] = this;
		LongArrayBitVector mergedBytes = mergeBytes(estimatorArray);
		return new HyperLogLog(mergedBytes.bits(), this.rsd, n);
	}

	public static LongArrayBitVector mergeBytes(ICardinality... estimators)
	{
		LongArrayBitVector mergedBytes = null;
		int numEsitimators = (estimators == null) ? 0 : estimators.length;
		if (numEsitimators > 0)
		{
			HyperLogLog estimator = (HyperLogLog) estimators[0];
			mergedBytes = LongArrayBitVector.ofLength(estimator.bitVector[0].length());

			for (int e = 0; e < numEsitimators; e++)
			{
				estimator = (HyperLogLog) estimators[e];
				mergedBytes.or(estimator.bitVector[0]);
			}
		}
		return mergedBytes;
	}

	public long[] getBits()
	{
		return bitVector[0].bits();
	}

	public int getRegisterSize()
	{
		return registerSize;
	}

	public static class Builder implements IBuilder<ICardinality>, Serializable
	{
		private static final long serialVersionUID = 2205437102378081334L;

		protected final double rsd;
		protected final long n;

		public Builder()
		{
			this(0.05);
		}

		public Builder(double rsd)
		{
			this(rsd, DEFAULT_NMAX);
		}

		public Builder(double rsd, long n)
		{
			this.rsd = rsd;
			this.n = n;
		}

		@Override
		public HyperLogLog build()
		{
			return new HyperLogLog(rsd, n);
		}

		@Override
		public int sizeof()
		{
			return 1 << log2NumberOfRegisters(rsd);
		}

		public static HyperLogLog build(byte[] bytes) throws IOException
		{
			ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
			DataInputStream oi = new DataInputStream(bais);
			long n = oi.readLong();
			double rsd = oi.readDouble();
			int size = oi.readInt();
			byte[] longArrayBytes = new byte[size];
			oi.readFully(longArrayBytes);
			return new HyperLogLog(getBits(longArrayBytes), rsd, n);
		}
	}
}
