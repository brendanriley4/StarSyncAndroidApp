from os.path import dirname, join
from skyfield.data import hipparcos
from skyfield.api import Star, load
from skyfield.api import wgs84
from datetime import datetime as dt


def getStarData(star_id, latitude, longitude):

    # Access de421.bsp
    de421 = join(dirname(__file__), 'de421.bsp')
    planets = load(de421)
    earth = planets['earth']

    # Access hip_main.dat
    hip_main = join(dirname(__file__), 'hip_main.dat')
    with open(hip_main, 'rb') as f:
        stars = hipparcos.load_dataframe(f)

    # Choose specific entry
    hipparcos_entry = Star.from_dataframe(stars.loc[star_id])

    # Load time and location data
    ts = load.timescale()
    t = ts.utc(dt.utcnow().year, dt.utcnow().month, dt.utcnow().day, dt.utcnow().hour, dt.utcnow().minute, dt.utcnow().second)

    # Define the observer's location
    observer = earth + wgs84.latlon(latitude, longitude)

    # Calculate the position of the star from the observer's location at the given time
    astrometric = observer.at(t).observe(hipparcos_entry)
    apparent = astrometric.apparent()

    # Calculate altitude and azimuth for visibility check
    alt, az, distance = apparent.altaz()
    visible = alt.degrees > 10

    # Return the necessary data including visibility
    return [alt.degrees, az.degrees, visible]