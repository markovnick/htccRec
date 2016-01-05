package org.jlab.rec.htcc;

import java.util.ArrayList;
import java.util.List;
import org.jlab.evio.clas12.EvioDataBank;
import org.jlab.evio.clas12.EvioDataDictionary;
import org.jlab.evio.clas12.EvioDataEvent;
import org.jlab.evio.clas12.EvioSource;

/**
 * Performs hit clustering for HTTC reconstruction.
 * 
 * @author J. Hankins
 * @author A. Puckett
 * @author G. Gavalian
 */
public class HTCCReconstruction {
    // HTCC geometry parameters
    private final ReconstructionParameters parameters;
    
    // Raw HTCC data from the bank
    private int[] hitnArray;
    private int[] sectorArray;
    private int[] ringArray;
    private int[] halfArray;
    private int[] npheArray;
    private double[] timeArray;
    private int[] ithetaArray;
    private int[] iphiArray;
    private int numHits;
    
    // Data about the hit in the remaining hit list with the greatest number of
    // photoelections. See findMaximumHit().
    private int maxHitNumPhotoelectrons;
    private int maxHitRemainingIndex;
    private int maxHitRawDataIndex;
    
    /**
     * Initializes the HTCCReconstruction.
     */
    public HTCCReconstruction() {
        parameters = new ReconstructionParameters();
    }
    
    /**
     * Clusters hits in the given event.
     * @param event the event containing hits to cluster
     */
    public void processEvent(EvioDataEvent event) {
        // Load the raw data about the event
        readBankInput(event);
        
        // Initialize the remaining hits list
        List<Integer> remainingHits = intiRemainingHitList();
        
        // Place all of the hits into clusters
        List<HTCCCluster> clusters = new ArrayList();
        HTCCCluster cluster;
        while (remainingHits.size() > 0 && (cluster = findCluster(remainingHits)) != null)
            clusters.add(cluster);
        
        // Push all of the clusters into the bank and print the results
        fillBankResults(clusters, event);
    }
    
    /**
     * Reads hit information from the given event out of the bank.
     * @param event the event under analysis
     */
    void readBankInput(EvioDataEvent event) {
        EvioDataBank bankDGTZ = (EvioDataBank) event.getBank("HTCC::dgtz");

        if (bankDGTZ.rows() == 0)
            return;
        
        hitnArray   = bankDGTZ.getInt("hitn");
        sectorArray = bankDGTZ.getInt("sector");
        ringArray   = bankDGTZ.getInt("ring");
        halfArray   = bankDGTZ.getInt("half");
        npheArray   = bankDGTZ.getInt("nphe");
        timeArray   = bankDGTZ.getDouble("time");
        
        
        numHits = hitnArray.length;
        
        // Create and fill ithetaArray and iphiArray so that the itheta and iphi
        // values are not calculated more than once
        ithetaArray = new int[numHits];
        iphiArray   = new int[numHits];
        for (int hit=0; hit<numHits; ++hit) {
            ithetaArray[hit] = ringArray[hit]-1;
            int iphi = 2*sectorArray[hit] + halfArray[hit] - 3;
            iphi = (iphi == 0 ? iphi + 12 : iphi) - 1;
            iphiArray[hit] = iphi;
        }
    }
    
    /**
     * Returns a list of of the indexes of the hits whose number of 
     * photoelectrons surpasses the minimum number of photoelectrons specified 
     * by in <code>parameters</code>.
     * @return a list of hit indexes
     */
    List<Integer> intiRemainingHitList() {
        List<Integer> remainingHits = new ArrayList();
        
        // Find all hits above the photoelectron threshold
        for (int hit=0; hit<numHits; ++hit) {
            if (npheArray[hit] > parameters.npheminhit) {
                remainingHits.add(hit);
            }
        }
        
        return remainingHits;
    }
    
    /**
     * Returns the next cluster or null if no clusters are left.
     * @param remainingHits the list of remaining hits
     * @return the next cluster or null if no clusters are left
     */
    HTCCCluster findCluster(List<Integer> remainingHits) {
        // Note:
        // maxHitNumPhotoelectrons : the number of photoelectrons for the maximum hit
        // maxHitRawDataIndex : the index of the hit in the raw data
        // maxHitRemainingIndex : the index of the hit in the remaining hits list
        
        // Find the hit from the list of remaining hits with the largest number 
        // of photoelectrons that also meets the threshold for the minimum 
        // number of photoelectrons specified by parameters.npheminmax
        findMaximumHit(remainingHits);
        System.out.printf("start clustering, number of hits total " + remainingHits.size() + " \n");

        // If a maximum hit was found:
        if (maxHitNumPhotoelectrons > 0) {
            
            // Remove the maximum hit from the list of remaining hits
            remainingHits.remove(maxHitRemainingIndex);
            
            // Get Hit Data:
            // Detector Indicies
            int    itheta = ithetaArray[maxHitRawDataIndex];
            int    iphi   = iphiArray[maxHitRawDataIndex];
            // Numver of Photoelectrons
            int    nphe   = maxHitNumPhotoelectrons;
            // Hit Time
            double time   = timeArray[maxHitRawDataIndex] - parameters.t0[itheta];
            // Detector Coordinates (polar)
            double theta  = parameters.theta0[itheta];
            double phi    = parameters.phi0 + 2.0*parameters.dphi0*iphi;
            // Detector Alignment Errors
            double dtheta = parameters.thetaRange;
            double dphi   = parameters.phiRange[itheta];
            
            // Create a new cluster and add the maximum hit
            HTCCCluster cluster = new HTCCCluster();
            cluster.addHit(itheta, iphi, nphe, time, theta, phi, dtheta, dphi);
                    
            // Recursively grow the cluster by adding nearby hits
            growCluster(cluster, remainingHits);
            
            //Check whether this cluster has nphe above threshold, size along theta and phi and total number of hits less than maximum:
            if (cluster.getNPheTot() >= parameters.npeminclst && 
                cluster.getNThetaClust() <= parameters.nthetamaxclst && 
                cluster.getNPhiClust() <= parameters.nphimaxclst && 
                cluster.getNHitClust() <= parameters.nhitmaxclst) {
                
                // Return the cluster
                return cluster;
            }
        }
        
        // There are no clusters left, so return null
        return null;
    }
    
    /**
     * Finds the hit from the list of remaining hits with the largest number of
     * photoelectrons that also meets the threshold for the minimum number of
     * photoelectrons specified in <code>parameters</code>.
     * <p>
     * Side effects:
     * If a maximum hit was found with a number of photo electrons greater than 
     * or equal to <code>parameters.npheminmax</code>, then:
     * maxHitNumPhotoelectrons = the number of photoelectrons for the max hit
     * maxHitRawDataIndex      = the index of the max hit in the bank data
     * maxHitRemainingIndex    = the index of the max hit in the remaining hits list
     * <p>
     * If no remaining hit has a number of photoelectrons greater than or equal 
     * to <code>parameters.npheminmax</code>, then:
     * maxHitNumPhotoelectrons = -1
     * maxHitRawDataIndex      = -1
     * maxHitRemainingIndex    = -1
     * 
     * @param remainingHits the list of remaining hits
     */
    void findMaximumHit(List<Integer> remainingHits) {
        maxHitNumPhotoelectrons = -1;
        maxHitRemainingIndex = -1;
        maxHitRawDataIndex = -1;
        for (int hit=0; hit<remainingHits.size(); ++hit) {
            int hitIndex = remainingHits.get(hit);
            int numPhotoElectrons = npheArray[hitIndex];
            if (numPhotoElectrons >= parameters.npheminmax && 
                numPhotoElectrons > maxHitNumPhotoelectrons) {
                maxHitNumPhotoelectrons = numPhotoElectrons;
                maxHitRemainingIndex = hit;
                maxHitRawDataIndex = hitIndex;
            }
        }
    }
    
    /**
     * Grows the given cluster by adding nearby hits from the remaining hits 
     * list.  As hits are added to the cluster they are removed from the 
     * remaining hits list.
     * @param cluster the cluster to grow
     * @param remainingHits the list of indexes of the remaining hits
     */
    void growCluster(HTCCCluster cluster, List<Integer> remainingHits) {
        // Get the average time of the cluster
        double clusterTime = cluster.getTime();
        // For each hit in the cluster:
        System.out.printf("start growing \n");
        for (int currHit=0; currHit<cluster.getNHitClust(); ++currHit) {
            // Get the hits coordinates
            int ithetaCurr = cluster.getHitITheta(currHit);
            int iphiCurr   = cluster.getHitIPhi(currHit);
             System.out.printf(" cluster cluster.getNHitClust() " + cluster.getNHitClust() + "\n");
             System.out.printf(" cluster phi " + iphiCurr + "\n");
             System.out.printf(" cluster theta " + ithetaCurr + "\n");
             
             
            // For each of the remaining hits:
            int hit = 0;
            System.out.printf(" hits remaining " + remainingHits.size() + "\n");

            while (hit < remainingHits.size()) {
                // Get the index of the remaining hit (and call it a test hit)

                int testHit = remainingHits.get(hit);
                // Get the coordinates of the test hit
                int ithetaTest = ithetaArray[testHit];
                int iphiTest   = iphiArray[testHit];
                System.out.printf(" hits remaining inside" + remainingHits.size() + "\n");

                 System.out.printf(" current phi " + iphiTest + "\n");
                System.out.printf(" current theta " + ithetaTest + "\n");
             
                // Find the distance
                int ithetaDiff = Math.abs(ithetaTest - ithetaCurr);
                int iphiDiff = Math.min((12+iphiTest-iphiCurr)%12, (12+iphiCurr-iphiTest)%12);
                // Find the difference in time
                double time = timeArray[testHit] - parameters.t0[ithetaTest];
                double timeDiff = Math.abs(time - clusterTime);
                // If the test hit is close enough in space and time
                if ((ithetaDiff == 1 || iphiDiff == 1) &&
                    (ithetaDiff + iphiDiff <= 2)&&
                    (timeDiff <= parameters.maxtimediff)) {
                    // Remove the hit from the remaining hits list
                    remainingHits.remove(hit);
                    // Get the Number of Photoelectrons
                    int    npheTest   = npheArray[testHit];
                    // Get the Detector Coordinates (polar)
                    double thetaTest  = parameters.theta0[ithetaTest];
                    double phiTest    = parameters.phi0 + 2.0*parameters.dphi0*iphiTest;
                    // Get the Detector Alignment Errors
                    double dthetaTest = parameters.thetaRange;
                    double dphiTest   = parameters.phiRange[ithetaTest];
                    // Add the hit to the cluster
                    cluster.addHit(ithetaTest, iphiTest, npheTest, time, thetaTest, phiTest, dthetaTest, dphiTest);
                    // Get the new average time of the cluster
                    clusterTime = cluster.getTime();
                } else {
                    // Go to the next hit in the remaining hits list
                    hit++;
                }
            }
        }
    }
    
    /**
     * Pushes 
     * @param clusters the output clusters
     * @param event the event under analysis
     */
    void fillBankResults(List<HTCCCluster> clusters, EvioDataEvent event) {
        // Determine the size of the output
        int size = clusters.size();
        
        if (size == 0)
            return;
        
        // Create the output bank
        EvioDataDictionary dict = (EvioDataDictionary) event.getDictionary();
        EvioDataBank bankClusters = (EvioDataBank) dict.createBank("HTCCRec::clusters", size);

        // Fill the output bank
        for (int i = 0; i < size; ++i) {
            HTCCCluster cluster = clusters.get(i);
            bankClusters.setInt("nhits", i, cluster.getNHitClust());
            bankClusters.setInt("ntheta", i, cluster.getNThetaClust());
            bankClusters.setInt("nphi", i, cluster.getNPhiClust());
            bankClusters.setInt("mintheta", i, cluster.getIThetaMin());
            bankClusters.setInt("maxtheta", i, cluster.getIThetaMax());
            bankClusters.setInt("minphi", i, cluster.getIPhiMin());
            bankClusters.setInt("maxphi", i, cluster.getIPhiMax());
            bankClusters.setInt("nphe", i, cluster.getNPheTot());
            bankClusters.setDouble("time", i, cluster.getTime());
            bankClusters.setDouble("theta", i, cluster.getTheta());
            bankClusters.setDouble("phi", i, cluster.getPhi());
            bankClusters.setDouble("dtheta", i, cluster.getDTheta());
            bankClusters.setDouble("dphi", i, cluster.getDPhi());
        }
        
        // Push the results into the bank
        event.appendBanks(bankClusters);
        
        // Display the results
    //    System.out.printf("\n[Detector-HTCC] >>>> zhopa huj Input hits %8d Output Clusters %8d\n", numHits, clusters.size());
        bankClusters.show();
    }
    
    
    /**
     * Contains the HTCC reconstruction parameters.
     */
    class ReconstructionParameters {
        double theta0[];
        double dtheta0[];
        double thetaRange;
        double phiRange[];
        double phi0;
        double dphi0;
        int npeminclst;
        int npheminmax;
        int npheminhit;
        int nhitmaxclst;
        int nthetamaxclst;
        int nphimaxclst;
        double maxtimediff;
        double t0[];
        
        /**
         * Initialize reconstruction parameters with sensible defaults.
         */
        ReconstructionParameters() {
            theta0 = new double[] { 8.75, 16.25, 23.75, 31.25 };
            dtheta0 = new double[] { 3.75, 3.75, 3.75, 3.75 } ;
            phiRange = new double[]{4, 2.4, 1.6, 1.2};

            for (int i=0; i<4; ++i) {
                theta0[i] = Math.toRadians(theta0[i]);
                dtheta0[i] = Math.toRadians(dtheta0[i]);
                phiRange[i] = Math.toRadians(phiRange[i]);
               
            }
            thetaRange = Math.toRadians(1.2);
            phi0 = Math.toRadians(15.0);
            dphi0 = Math.toRadians(15.0);
            npeminclst = 1;
            npheminmax = 1;
            npheminhit = 1;
            nhitmaxclst = 4;
            nthetamaxclst = 2;
            nphimaxclst = 2;
            maxtimediff = 2;
            t0 = new double[] { 11.553, 11.943, 12.339, 12.75 };
        }
        
        /**
         * Initialize reconstruction parameters from a packed string.
         * @param packed_string the packed string
         * @throws UnsupportedOperationException
         */
        ReconstructionParameters(String packed_string) {
            // TODO if necessary
            throw new UnsupportedOperationException();
        }
    }
    
    /**
     * Main routine for testing.
     * 
     * The environment variable $CLAS12DIR must be set and point to a directory 
     * that contains lib/bankdefs/clas12/<dictionary file name>.xml
     *
     * @param args ignored
     */
    public static void main(String[] args){
        String inputfile = "out.ev";
        
        EvioSource reader = new EvioSource();
        reader.open(inputfile);
        
        HTCCReconstruction htccRec = new HTCCReconstruction();
        while(reader.hasEvent()){
            EvioDataEvent event = (EvioDataEvent) reader.getNextEvent();
            htccRec.processEvent(event);
        }
    }
}
