/* -*- java -*- ************************************************************************** *
 *
 *                     Copyright (C) KNAPP AG
 *
 *   The copyright to the computer program(s) herein is the property
 *   of Knapp.  The program(s) may be used   and/or copied only with
 *   the  written permission of  Knapp  or in  accordance  with  the
 *   terms and conditions stipulated in the agreement/contract under
 *   which the program(s) have been supplied.
 *
 * *************************************************************************************** */

package com.knapp.codingcontest.kcc2021.solution;

import java.util.*;
import java.util.stream.Collectors;

import com.knapp.codingcontest.kcc2021.data.InputData;
import com.knapp.codingcontest.kcc2021.data.Institute;
import com.knapp.codingcontest.kcc2021.data.Packet;
import com.knapp.codingcontest.kcc2021.data.Pallet;
import com.knapp.codingcontest.kcc2021.data.Pallet.PacketPos;
import com.knapp.codingcontest.kcc2021.data.PalletType;
import com.knapp.codingcontest.kcc2021.warehouse.*;

/**
 * This is the code YOU have to provide
 * <p>
 * //@param warehouse all the operations you should need
 */
public class Solution {
    public String getParticipantName() {
        return "Unfried Robert"; // TODO: return your name
    }

    public Institute getParticipantInstitution() {
        return Institute.HTL_Rennweg_Wien; // TODO: return the Id of your institute - please refer to the hand-out
    }

    // ----------------------------------------------------------------------------

    protected final InputData input;
    protected final Warehouse warehouse;

    // ----------------------------------------------------------------------------

    //Defining Variables
    List<Packet> allPackets;
    LinkedList<PalletType> palletTypes;
    LinkedList<Pallet> oldPallets = new LinkedList<>();

    public Solution(final Warehouse warehouse, final InputData input) {
        this.input = input;
        this.warehouse = warehouse;
        allPackets = input.getPackets().stream()
                .sorted(Comparator.comparingInt(Packet::getTruckId)
                        .thenComparingInt(p -> (p.getWidth() * p.getLength()))
                        .thenComparingInt(Packet::getWeight))
                .collect(Collectors.toList());
        palletTypes = input.getPalletTypes().stream().sorted(Comparator.comparingInt(p -> (p.getWidth() * p.getLength())))
                .collect(Collectors.toCollection(LinkedList::new));
    }

    // ----------------------------------------------------------------------------

    /**
     * The main entry-point
     */
    public void run() throws Exception {
        int currentTruckNum = allPackets.get(0).getTruckId();
        Pallet pallet = warehouse.preparePallet(currentTruckNum, palletTypes.get(0));
        Packet packet;

        for (int index = 0; index < allPackets.size(); index++) {
            packet = allPackets.get(index);
            if (pallet.getTruckId() != packet.getTruckId()) {
                currentTruckNum = packet.getTruckId();
                oldPallets = new LinkedList<>();
                pallet = warehouse.preparePallet(currentTruckNum, calculateBestPalletType(packet));
            }
            if (recyclePallets(packet)) {
                continue;
            }
            if (placePacket(packet, pallet)) {
                continue;
            }
            oldPallets.add(pallet);
            pallet = warehouse.preparePallet(currentTruckNum, calculateBestPalletType(packet));
            index--;
        }
    }

    //calculate PalletType for given packet
    public PalletType calculateBestPalletType(Packet packet) {
        return palletTypes.stream().filter(p -> (p.getLength() >= packet.getLength()) &&
                        (p.getWidth() >= packet.getWidth()))
                .filter(p -> p.getMaxWeight() >= packet.getWeight()).findFirst().get();
    }

    //check if packet fits onto already used pallets
    public boolean recyclePallets(Packet packet) {
        oldPallets = oldPallets.stream().sorted(Comparator.comparingInt(p -> p.getType().getLength() * p.getType()
                .getWidth())).collect(Collectors.toCollection(LinkedList::new));
        for (Pallet p : oldPallets) {
            try {
                if (findLocation(packet, p)) {
                    return true;
                }
            } catch (Exception e) {
                //ignored
            }
        }
        for (Pallet p : oldPallets) {
            try {
                warehouse.putPacket(p, packet, 0, 0, false);
                return true;
            } catch (Exception e) {
                //ignored
            }
        }
        return false;
    }

    //place packet on pallet
    public boolean placePacket(Packet packet, Pallet pallet) throws Exception {
        Pallet.Layer l = pallet.getLayer(pallet.getCurrentStackedHeight() - 1);
        try {
            l.getPackets();
        } catch (NullPointerException e) {
            try {
                warehouse.putPacket(pallet, packet, 0, 0, false);
                return true;
            } catch (HeightExceededException | WeightExceededException | PalletExtendsViolatedException e2) {
                return false;
            }
        }
        if (findLocation(packet, pallet)) {
            return true;
        }
        try {
            warehouse.putPacket(pallet, packet, 0, 0, false);
        } catch (HeightExceededException | WeightExceededException | PalletExtendsViolatedException e3) {
            return false;
        }
        return true;
    }

    //calculate best position in one layer
    public boolean findLocation(Packet packet, Pallet pallet) throws Exception {
        Pallet.Layer l = pallet.getLayer(pallet.getCurrentStackedHeight() - 1);
        int layerStack = pallet.getCurrentStackedHeight();
        try {
            l.getPackets();
        } catch (NullPointerException e) {
            warehouse.putPacket(pallet, packet, 0, 0, false);
            return true;
        }
        //building the map
        boolean[][] map = new boolean[pallet.getType().getWidth()][pallet.getType().getLength()];
        for (Map.Entry<PacketPos, Packet> e : l.getPackets().entrySet()) {
            for (int i = 0; i < e.getValue().getWidth(); i++) {
                for (int j = 0; j < e.getValue().getLength(); j++) {
                    if (!e.getKey().isRotated()) {
                        map[e.getKey().getY() + i][e.getKey().getX() + j] = true;
                    } else {
                        map[e.getKey().getY() + j][e.getKey().getX() + i] = true;
                    }
                }
            }
        }

        boolean validNom;
        boolean validRot;
        //try to place packets
        for (int i = 0; i < map.length; i++) {
            for (int j = 0; j < map[i].length; j++) {
                validNom = true;
                validRot = true;
                outer:
                for (int p = 0; p < packet.getWidth(); p++) {
                    for (int b = 0; b < packet.getLength(); b++) {

                        if (i + p > map.length - 1 || j + b > map[0].length - 1) {
                            validNom = false;
                        } else if (map[i + p][j + b]) {
                            validNom = false;
                        }
                        if (i + b > map.length - 1 || j + p > map[0].length - 1) {
                            validRot = false;
                        } else if (map[i + b][j + p]) {
                            validRot = false;
                        }
                        if (!validNom && !validRot) break outer;

                    }
                }
                try {
                    if (validNom) {
                        warehouse.putPacket(pallet, packet, j, i, false);
                        return true;
                    } else if (validRot) {
                        warehouse.putPacket(pallet, packet, j, i, true);
                        return true;
                    }
                } catch (WeightExceededException e) {
                    return false;
                }
            }
        }
        return false;
    }

    // ----------------------------------------------------------------------------
    // ----------------------------------------------------------------------------

    /**
     * Just for documentation purposes.
     * <p>
     * Method may be removed without any side-effects
     * <p>
     * divided into 4 sections
     *
     * <li><em>input methods</em>
     *
     * <li><em>main interaction methods</em>
     * - these methods are the ones that make (explicit) changes to the warehouse
     *
     * <li><em>information</em>
     * - information you might need for your solution
     *
     * <li><em>additional information</em>
     * - various other infos: statistics, information about (current) costs, ...
     */
    @SuppressWarnings("unused")
    private void apis() throws Exception {
        // ----- input -----

        final PalletType palletType = input.getPalletTypes().iterator().next();
        final Packet packet = input.getPackets().iterator().next();

        // ----- main interaction methods -----

        final Pallet pallet = warehouse.preparePallet(packet.getTruckId(), palletType);

        final int x = 0;
        final int y = 0;
        final boolean rotated = false;
        warehouse.putPacket(pallet, packet, x, y, rotated);

        // ----- information -----
        final int csh = pallet.getCurrentStackedHeight();
        final int cw = pallet.getCurrentWeight();
        final Pallet.Layer layer = pallet.getLayer(0);
        final Map<PacketPos, Packet> lpackets = layer.getPackets();

        // ----- additional information -----
        final WarehouseInfo info = warehouse.getInfo();

        final long tc = info.getTotalCost();
        final long upc = info.getUnfinishedPacketsCost();
        final long pac = info.getPalletsAreaCost();
        final long pvuc = info.getPalletsVolumeUsedCost();

        final int up = info.getUnfinishedPacketCount();
        final long pc = info.getPalletCount();
        final long pa = info.getPalletsArea();
        final long pvu = info.getPalletsVolumeUsed();
    }

    // ----------------------------------------------------------------------------
}
