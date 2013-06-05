/*
 * Coordinate Transformations Suite (abridged CTS)  is a library developped to 
 * perform Coordinate Transformations using well known geodetic algorithms 
 * and parameter sets. 
 * Its main focus are simplicity, flexibility, interoperability, in this order.
 *
 * This library has been originaled developed by Michael Michaud under the JGeod
 * name. It has been renamed CTS in 2009 and shared to the community from 
 * the Atelier SIG code repository.
 * 
 * Since them, CTS is supported by the Atelier SIG team in collaboration with Michael 
 * Michaud.
 * The new CTS has been funded  by the French Agence Nationale de la Recherche 
 * (ANR) under contract ANR-08-VILL-0005-01 and the regional council 
 * "Région Pays de La Loire" under the projet SOGVILLE (Système d'Orbservation 
 * Géographique de la Ville).
 *
 * CTS is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * CTS is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * CTS. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <https://github.com/irstv/cts/>
 */
package org.cts.op.projection;

import java.util.Map;
import org.cts.CoordinateDimensionException;
import org.cts.Ellipsoid;
import org.cts.Identifier;
import org.cts.units.Measure;
import static java.lang.Math.*;
import org.cts.CoordinateOperation;
import org.cts.NonInvertibleOperationException;

/**
 * The Polar Stereographic Projection (STERE). <p>
 *
 * @author Jules Party
 */
public class Stereographic extends Projection {

    public static final Identifier STERE =
            new Identifier("EPSG", "9810", "Polar Stereographic", "STERE");
    protected final double lat0, // the reference latitude
            lon0, // the reference longitude (from the datum prime meridian)
            xs, // x coordinate of the pole
            ys,   // y coordinate of the pole
            k0, // scale coefficent for easting
            a, // semi major axis
            e, // eccentricity of the ellipsoid
            e2; // square eccentricity of the ellipsoid
    private double PI_2 = PI/2;

    public Stereographic(final Ellipsoid ellipsoid,
            final Map<String, Measure> parameters) {
        super(STERE, ellipsoid, parameters);
        lon0 = getCentralMeridian();
        lat0 = getLatitudeOfOrigin();
        xs = getFalseEasting();
        ys = getFalseNorthing();
        e = ellipsoid.getEccentricity();
        e2 = ellipsoid.getSquareEccentricity();
        if (abs(getLatitudeOfTrueScale()) != PI_2) {
            double lat_ts = getLatitudeOfTrueScale();
            double esints = e * sin(lat_ts);
            double tf = tan((PI_2+lat_ts)/2) / pow ((1+esints)/(1-esints), e/2);
            double mf = cos(lat_ts)/pow(1 - esints*esints, 0.5);
            k0 = mf * pow(pow(1 + e, 1 + e) * pow(1 - e, 1 - e), 0.5) / 2 / tf;
        } else {
            k0 = getScaleFactor();
        }
        a = getSemiMajorAxis();
    }

    /**
     * Return the
     * <code>Surface</code> type of this
     * <code>Projection</code>.
     */
    @Override
    public Surface getSurface() {
        return Projection.Surface.AZIMUTHAL;
    }

    /**
     * Return the
     * <code>Property</code> of this
     * <code>Projection</code>.
     */
    @Override
    public Property getProperty() {
        return Projection.Property.CONFORMAL;
    }

    /**
     * Return the
     * <code>Orientation</code> of this
     * <code>Projection</code>.
     */
    @Override
    public Orientation getOrientation() {
        return Projection.Orientation.TANGENT;
    }

    /**
     * Transform coord using the Mercator Projection. Input coord is supposed to
     * be a geographic latitude / longitude coordinate in radians.
     * Algorithm based on the OGP's Guidance Note Number 7 Part 2 :
     * <http://www.epsg.org/guides/G7-2.html>
     *
     * @param coord coordinate to transform
     * @throws CoordinateDimensionException if <code>coord</code> length is not
     * compatible with this <code>CoordinateOperation</code>.
     */
    @Override
    public double[] transform(double[] coord) throws CoordinateDimensionException {
        double lon = coord[1];
        double lat = coord[0];
        double esin = e * sin(lat);
        double t;
        if (lat0 < 0) {
            t = tan((PI_2 + lat) / 2) / pow((1 + esin) / (1 - esin), e / 2);
        } else {
            t = tan((PI_2 - lat) / 2) * pow((1 + esin) / (1 - esin), e / 2);
        }
        double rho = 2 * a * k0 * t / pow(pow(1 + e, 1 + e) * pow(1 - e, 1 - e), 0.5);
        double dE = rho * sin(lon - lon0);
        double dN = rho * cos(lon - lon0);
        coord[0] = xs + dE;
        if (lat0 < 0) {
            coord[1] = ys + dN;
        } else {
            coord[1] = ys - dN;
        }
        return coord;
    }
    
    /**
     * Creates the inverse operation for Mercator Projection.
     * Input coord is supposed to be a projected easting / northing coordinate in meters.
     * Algorithm based on the OGP's Guidance Note Number 7 Part 2 :
     * <http://www.epsg.org/guides/G7-2.html>
     * 
     * @param coord coordinate to transform
     */
    @Override
    public CoordinateOperation inverse() throws NonInvertibleOperationException {
        return new Stereographic(ellipsoid, parameters) {

            @Override
            public double[] transform(double[] coord) throws CoordinateDimensionException {
                double rho = pow((coord[0] - xs) * (coord[0] - xs) + (coord[1] - ys) * (coord[1] - ys), 0.5);
                double t = rho * pow(pow(1 + e, 1 + e) * pow(1 - e, 1 - e), 0.5) / 2 / a / k0;
                double ki;
                if (lat0 > 0) {
                    ki = PI / 2 - 2 * atan(t);
                } else {
                    ki = 2 * atan(t) - PI / 2;
                }
                double lat = ki;
                for (int i = 1; i < 5; i++) {
                    lat += ellipsoid.getInverseMercatorCoeff()[i] * sin(2 * i * ki);
                }
                if (lat0 < 0) {
                    coord[1] = lon0 + atan2(coord[0] - xs, coord[1] - ys);
                } else {
                    coord[1] = lon0 + atan2(coord[0] - xs, ys - coord[1]);
                }
                coord[0] = lat;
                return coord;
            }
        };
    }
}