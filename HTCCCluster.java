package org.jlab.rec.htcc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author J. Hankins
 * @author A. Puckett
 * @author G. Gavalian
 */
class HTCCCluster {

    private int nhitclust;
    private int nthetaclust;
    private int nphiclust;

    private int ithetamin;
    private int ithetamax;
    private int iphimin;
    private int iphimax;

    private int nphetot;

    private double theta;
    private double dtheta;
    private double phi;
    private double dphi;
    private double time;

    private final List<Integer> hitnphe;
    private final List<Integer> hititheta;
    private final List<Integer> hitiphi;
    private final Set<Integer> setitheta;
    private final Set<Integer> setiphi;
    private final List<Double> hittheta;
    private final List<Double> hitphi;
    private final List<Double> hitdtheta;
    private final List<Double> hitdphi;
    private final List<Double> hittime;

    HTCCCluster() {
        nhitclust = 0;
        nthetaclust = 0;
        nphiclust = 0;

        ithetamin = 0;
        ithetamax = 0;
        iphimin = 0;
        iphimax = 0;

        nphetot = 0;

        theta = 0.0;
        dtheta = 0.0;
        phi = 0.0;
        dphi = 0.0;
        time = 0.0;

        hitnphe = new ArrayList<Integer>();
        hititheta = new ArrayList<Integer>();
        hitiphi = new ArrayList<Integer>();
        hittheta = new ArrayList<Double>();
        hitphi = new ArrayList<Double>();
        hitdtheta = new ArrayList<Double>();
        hitdphi = new ArrayList<Double>();
        hittime = new ArrayList<Double>();
        setitheta = new HashSet<Integer>();
        setiphi = new HashSet<Integer>();
    }

    void addHit(int itheta, int iphi, int nphe, double time, double theta, double phi, double dtheta, double dphi) {
        // TODO remove after testing
        if (!(0 <= itheta && itheta < 4)) {
            throw new IllegalArgumentException("itheta");
        }
        if (!(0 <= iphi && iphi < 12)) {
            throw new IllegalArgumentException("iphi");
        }
        if (!(0 <= nphe)) {
            throw new IllegalArgumentException("nphe");
        }
        setitheta.add(itheta);
        setiphi.add(iphi);
        hititheta.add(itheta);
        hitiphi.add(iphi);
        hitnphe.add(nphe);
        hittime.add(time);
        hittheta.add(theta);
        hitphi.add(phi);
        hitdtheta.add(Math.abs(dtheta)); // force errors to be positive
        hitdphi.add(Math.abs(dphi)); // force errors to be positive

        calcSums();
    }

    void calcSums() {
        time = 0.0;
        theta = 0.0;
        phi = 0.0;
        dtheta = 0.0;
        dphi = 0.0;
        double thetaTemp = 0.0;
        double phiTemp = 0.0;

        nphetot = 0;

        nhitclust = hitnphe.size();

        double cosphi = 0.0;
        double sinphi = 0.0;
       

        System.out.printf("n hits in da cluster " + nhitclust + "\n");
        for (int i = 0; i < nhitclust; i++) {
        thetaTemp = +hittheta.get(i);
        phiTemp = +hitphi.get(i);
        System.out.printf("theta temp calc" + i + " : " + thetaTemp + "\n");

        }
        thetaTemp /= nhitclust;
        phiTemp /= nhitclust;
        
        for (int i = 0; i < nhitclust; i++) {

            if (i == 0 || hititheta.get(i) > ithetamax) {
                ithetamax = hititheta.get(i);
            }
            if (i == 0 || hititheta.get(i) < ithetamin) {
                ithetamin = hititheta.get(i);
            }
            if (i == 0 || hitiphi.get(i) > iphimax) {
                iphimax = hitiphi.get(i);
            }
            if (i == 0 || hitiphi.get(i) < iphimin) {
                iphimin = hitiphi.get(i);
            }
 
            nphetot += hitnphe.get(i);
            
            time += hittime.get(i) * hitnphe.get(i);
            System.out.printf("hittime.get(i) " + hittime.get(i) + "\n");
            
            System.out.printf("npe " + i + " : " + hitnphe.get(i) + "\n");
            System.out.printf("sum npe " + i + " : " + nphetot + "\n");
            theta += (hittheta.get(i) + hitdtheta.get(i)*Math.signum(hittheta.get(i) - thetaTemp))* hitnphe.get(i);
            System.out.printf("theta temp " + i + " : " + thetaTemp + "\n");

            System.out.printf("theta " + i + " : " + hittheta.get(i) + "\n");
            System.out.printf("hititheta  " + i + " : " + hititheta.get(i) + "\n");

            System.out.printf("dtheta " + hitdtheta.get(i) + "\n");
            System.out.printf("coeff theta" + i + " : " + Math.signum(hittheta.get(i) - thetaTemp)+ "\n");
            System.out.printf("sum theta " + i + " : " + theta + "\n");

            cosphi += Math.cos(hitphi.get(i) + hitdphi.get(i)*Math.signum(hitphi.get(i) - phiTemp)) * hitnphe.get(i);
            sinphi += Math.sin(hitphi.get(i) + hitdphi.get(i)*Math.signum(hitphi.get(i) - phiTemp)) * hitnphe.get(i);

            System.out.printf("phi " + i + " : " + hitphi.get(i) + "\n");
            System.out.printf("dphi " + hitdphi.get(i) + "\n");
            System.out.printf("coeff phi" + i + " : " + Math.signum(hitphi.get(i) - phiTemp)+ "\n");
            System.out.printf("hitiphi  " + i + " : " + hitiphi.get(i) + "\n");
            System.out.printf("phi temp " + i + " : " + phiTemp + "\n");

            System.out.printf("sum cosphi " + i + " : " + cosphi + "\n");

            
   

        
        }
        time /= nphetot; // weighted average

        //      theta /= dtheta; //include dtheta weight
        theta /= nphetot; //include npe weight
        System.out.printf("res theta " + theta + "\n");

        //       cosphi /= dphi;
        //       sinphi /= dphi;
        cosphi /= nphetot;
        sinphi /= nphetot;
        phi = Math.atan2(sinphi, cosphi);

        dtheta = Math.pow(dtheta, -0.5);
        dphi = Math.pow(dphi, -0.5);

        nthetaclust = setitheta.size();
        nphiclust = setiphi.size();
    }

    public int getNPheTot() {
        return nphetot;
    }

    public int getNThetaClust() {
        return nthetaclust;
    }

    public int getNPhiClust() {
        return nphiclust;
    }

    public int getNHitClust() {
        return nhitclust;
    }

    public int getIThetaMin() {
        return ithetamin;
    }

    public int getIThetaMax() {
        return ithetamax;
    }

    public int getIPhiMin() {
        return iphimin;
    }

    public int getIPhiMax() {
        return iphimax;
    }

    public double getTime() {
        return time;
    }

    public double getTheta() {
        return theta;
    }

    public double getPhi() {
        return phi;
    }

    public double getDTheta() {
        return dtheta;
    }
   

    public double getDPhi() {
        return dphi;
    }

    public int getHitITheta(int hit) {
        return hititheta.get(hit);
    }

    public int getHitIPhi(int hit) {
        return hitiphi.get(hit);
    }

}
