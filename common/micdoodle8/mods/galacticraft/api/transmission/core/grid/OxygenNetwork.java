package micdoodle8.mods.galacticraft.api.transmission.core.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasAcceptor;
import micdoodle8.mods.galacticraft.api.transmission.NetworkType;
import micdoodle8.mods.galacticraft.api.transmission.compatibility.NetworkConfigHandler;
import micdoodle8.mods.galacticraft.api.transmission.core.path.Pathfinder;
import micdoodle8.mods.galacticraft.api.transmission.core.path.PathfinderChecker;
import micdoodle8.mods.galacticraft.api.transmission.tile.IConductor;
import micdoodle8.mods.galacticraft.api.transmission.tile.IConnector;
import micdoodle8.mods.galacticraft.api.transmission.tile.INetworkConnection;
import micdoodle8.mods.galacticraft.api.transmission.tile.INetworkProvider;
import micdoodle8.mods.galacticraft.api.transmission.tile.IOxygenReceiver;
import micdoodle8.mods.galacticraft.api.transmission.tile.ITransmitter;
import micdoodle8.mods.galacticraft.api.vector.Vector3;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import cpw.mods.fml.common.FMLLog;

/**
 * An Electrical Network specifies a wire connection. Each wire connection line will have its own
 * electrical network.
 * 
 * !! Do not include this class if you do not intend to have custom wires in your mod. This will
 * increase future compatibility. !!
 * 
 * @author Calclavia
 * 
 */
public class OxygenNetwork implements IOxygenNetwork
{
	public Map<TileEntity, ArrayList<ForgeDirection>> oxygenTiles = new HashMap<TileEntity, ArrayList<ForgeDirection>>();

	private final Set<ITransmitter> pipes = new HashSet<ITransmitter>();

	@Override
	public float produce(float totalEnergy, TileEntity... ignoreTiles)
	{
		float remainingUsableOxygen = totalEnergy;

		Set<TileEntity> avaliableEnergyTiles = this.getAcceptors();

		if (!avaliableEnergyTiles.isEmpty())
		{
			final float totalOxygenRequest = this.getRequest(ignoreTiles);

			if (totalOxygenRequest > 0)
			{
				for (TileEntity tileEntity : avaliableEnergyTiles)
				{
					if (!Arrays.asList(ignoreTiles).contains(tileEntity))
					{
						if (tileEntity instanceof IOxygenReceiver)
						{
							IOxygenReceiver electricalTile = (IOxygenReceiver) tileEntity;

							for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
							{
								TileEntity tile = new Vector3(tileEntity).modifyPositionFromSide(direction).getTileEntity(tileEntity.worldObj);
																
								if (electricalTile.canConnect(direction, NetworkType.OXYGEN) && this.getTransmitters().contains(tile))
								{
									float oxygenToSend = totalEnergy * (electricalTile.getOxygenRequest(direction) / totalOxygenRequest);

									if (oxygenToSend > 0)
									{
										remainingUsableOxygen -= ((IOxygenReceiver) tileEntity).receiveOxygen(direction, oxygenToSend, true);
									}
								}
							}
						}
						else if (NetworkConfigHandler.isMekanismLoaded() && tileEntity instanceof IGasAcceptor)
						{
							IGasAcceptor gasAcceptor = (IGasAcceptor) tileEntity;

							for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
							{
								TileEntity tile = new Vector3(tileEntity).modifyPositionFromSide(direction).getTileEntity(tileEntity.worldObj);
																
								if (gasAcceptor.canReceiveGas(direction, (Gas) NetworkConfigHandler.gasOxygen) && this.getTransmitters().contains(tile))
								{
									int oxygenToSend = (int) Math.floor(totalEnergy / (float)avaliableEnergyTiles.size()); // TODO: Mekanism PR to simulate received gas amount

									if (oxygenToSend > 0)
									{
										remainingUsableOxygen -= ((IGasAcceptor) tileEntity).receiveGas(new GasStack((Gas) NetworkConfigHandler.gasOxygen, oxygenToSend));
									}
								}
							}
						}
					}
				}
			}
		}

		return remainingUsableOxygen;
	}

	/**
	 * @return How much electricity this network needs.
	 */
	@Override
	public float getRequest(TileEntity... ignoreTiles)
	{
		List<Float> requests = new ArrayList<Float>();

		Iterator<TileEntity> it = this.getAcceptors().iterator();

		while (it.hasNext())
		{
			TileEntity tileEntity = it.next();

			if (Arrays.asList(ignoreTiles).contains(tileEntity))
			{
				continue;
			}

			if (tileEntity instanceof IOxygenReceiver)
			{
				if (!tileEntity.isInvalid())
				{
					if (tileEntity.worldObj.getBlockTileEntity(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord) == tileEntity)
					{
						for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
						{
							Vector3 tileVec = new Vector3(tileEntity);
							TileEntity tile = tileVec.modifyPositionFromSide(direction).getTileEntity(tileEntity.worldObj);
							
							if (((IOxygenReceiver) tileEntity).canConnect(direction, NetworkType.OXYGEN) && this.getTransmitters().contains(tile))
							{
								requests.add(((IOxygenReceiver) tileEntity).getOxygenRequest(direction));
								continue;
							}
						}
					}
				}
			}
		}

		float total = 0.0F;
		
		for (Float f : requests)
		{
			total += f;
		}
		
		return total / (float)requests.size();
	}

	/**
	 * @return Returns all producers in this electricity network.
	 */
	@Override
	public Set<TileEntity> getAcceptors()
	{
		return this.oxygenTiles.keySet();
	}

	/**
	 * @param tile The tile to get connections for
	 * @return The list of directions that can be connected to for the provided tile
	 */
	@Override
	public ArrayList<ForgeDirection> getPossibleDirections(TileEntity tile)
	{
		return this.oxygenTiles.containsKey(tile) ? this.oxygenTiles.get(tile) : null;
	}

	/**
	 * This function is called to refresh all conductors in this network
	 */
	@Override
	public void refresh()
	{
		this.oxygenTiles.clear();

		try
		{
			Iterator<ITransmitter> it = this.pipes.iterator();

			while (it.hasNext())
			{
				ITransmitter transmitter = it.next();

				if (transmitter == null)
				{
					it.remove();
				}
				else if (((TileEntity) transmitter).isInvalid())
				{
					it.remove();
				}
				else
				{
					transmitter.setNetwork(this);
				}

				for (int i = 0; i < transmitter.getAdjacentConnections().length; i++)
				{
					TileEntity acceptor = transmitter.getAdjacentConnections()[i];

					if (!(acceptor instanceof IConductor) && acceptor instanceof IConnector)
					{
						ArrayList<ForgeDirection> possibleDirections = null;

						if (this.oxygenTiles.containsKey(acceptor))
						{
							possibleDirections = this.oxygenTiles.get(acceptor);
						}
						else
						{
							possibleDirections = new ArrayList<ForgeDirection>();
						}

						possibleDirections.add(ForgeDirection.getOrientation(i));

						this.oxygenTiles.put(acceptor, possibleDirections);
					}
				}
			}
		}
		catch (Exception e)
		{
			FMLLog.severe("Universal Electricity: Failed to refresh conductor.");
			e.printStackTrace();
		}
	}

	@Override
	public Set<ITransmitter> getTransmitters()
	{
		return this.pipes;
	}

	@Override
	public IOxygenNetwork merge(IOxygenNetwork network)
	{
		if (network != null && network != this)
		{
			OxygenNetwork newNetwork = new OxygenNetwork();
			newNetwork.getTransmitters().addAll(this.getTransmitters());
			newNetwork.getTransmitters().addAll(network.getTransmitters());
			newNetwork.refresh();
			return newNetwork;
		}
		
		return this;
	}

	@Override
	public void split(ITransmitter splitPoint)
	{
		if (splitPoint instanceof TileEntity)
		{
			this.getTransmitters().remove(splitPoint);

			/**
			 * Loop through the connected blocks and attempt to see if there are connections between
			 * the two points elsewhere.
			 */
			TileEntity[] connectedBlocks = splitPoint.getAdjacentConnections();

			for (int i = 0; i < connectedBlocks.length; i++)
			{
				TileEntity connectedBlockA = connectedBlocks[i];

				if (connectedBlockA instanceof INetworkConnection)
				{
					for (int ii = 0; ii < connectedBlocks.length; ii++)
					{
						final TileEntity connectedBlockB = connectedBlocks[ii];

						if (connectedBlockA != connectedBlockB && connectedBlockB instanceof INetworkConnection)
						{
							Pathfinder finder = new PathfinderChecker(((TileEntity) splitPoint).worldObj, (INetworkConnection) connectedBlockB, NetworkType.OXYGEN, splitPoint);
							finder.init(new Vector3(connectedBlockA));

							if (finder.results.size() > 0)
							{
								/**
								 * The connections A and B are still intact elsewhere. Set all
								 * references of wire connection into one network.
								 */

								for (Vector3 node : finder.closedSet)
								{
									TileEntity nodeTile = node.getTileEntity(((TileEntity) splitPoint).worldObj);

									if (nodeTile instanceof INetworkProvider)
									{
										if (nodeTile != splitPoint)
										{
											((INetworkProvider) nodeTile).setNetwork(this);
										}
									}
								}
							}
							else
							{
								/**
								 * The connections A and B are not connected anymore. Give both of
								 * them a new network.
								 */
								IOxygenNetwork newNetwork = new OxygenNetwork();

								for (Vector3 node : finder.closedSet)
								{
									TileEntity nodeTile = node.getTileEntity(((TileEntity) splitPoint).worldObj);

									if (nodeTile instanceof INetworkProvider)
									{
										if (nodeTile != splitPoint)
										{
											newNetwork.getTransmitters().add((ITransmitter) nodeTile);
										}
									}
								}

								newNetwork.refresh();
							}
						}
					}
				}
			}
		}
	}

	@Override
	public String toString()
	{
		return "OxygenNetwork[" + this.hashCode() + "|Pipes:" + this.pipes.size() + "|Acceptors:" + this.oxygenTiles.size() + "]";
	}
}
