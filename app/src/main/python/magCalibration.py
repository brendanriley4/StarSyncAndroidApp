import numpy as np
import matplotlib.pyplot as plt
from scipy.optimize import least_squares
from mpl_toolkits.mplot3d import Axes3D


# Function to handle incoming data
def process_data(data_string):
    # Split the long string into a list of strings at each position where a new set of XYZ starts
    data_list = data_string.split(',')
    grouped_data = [data_list[n:n+3] for n in range(0, len(data_list), 3)]

    # Convert these groups to floats and create a numpy array
    data = np.array([list(map(float, group)) for group in grouped_data])

    # Transpose so that each row is one component (x, y, z) across all measurements
    magM = data.T
    return magM


def transform_mag_data(mag_data, x0, y0, z0, a, b, c, alpha, beta, gamma):

    # Translate data to move the ellipsoid center to the origin
    translated_data = mag_data - np.array([[x0], [y0], [z0]])

    # Create rotation matrices for each axis
    Rx = np.array([[1, 0, 0],
                   [0, np.cos(alpha), -np.sin(alpha)],
                   [0, np.sin(alpha), np.cos(alpha)]])
    Ry = np.array([[np.cos(beta), 0, np.sin(beta)],
                   [0, 1, 0],
                   [-np.sin(beta), 0, np.cos(beta)]])
    Rz = np.array([[np.cos(gamma), -np.sin(gamma), 0],
                   [np.sin(gamma), np.cos(gamma), 0],
                   [0, 0, 1]])

    # Complete rotation matrix
    R = Rx @ Ry @ Rz

    # Scale the data according to the ellipsoid axes
    S = np.diag([1/a, 1/b, 1/c])

    # Apply the rotations and scaling
    transformed_data = R @ S @ translated_data

    return transformed_data


def ellipsoid_fit(mag_data):
    # Initial guess for the ellipsoid parameters
    # x0, y0, z0 (ellipsoid center), a, b, c (semi-axes lengths), angles defining orientation
    x0, y0, z0 = np.mean(mag_data, axis=1)
    initial_guess = [x0, y0, z0, 1, 1, 1, 0, 0, 0]  # Simplified guess

    def residuals(params):
        x0, y0, z0, a, b, c, alpha, beta, gamma = params
        # Transform data based on current parameters
        transformed = transform_mag_data(mag_data, x0, y0, z0, a, b, c, alpha, beta, gamma)
        # Calculate how far off from being a sphere
        sphere_center = np.mean(transformed, axis=1)
        distances = np.sqrt(np.sum((transformed - sphere_center[:, None])**2, axis=0))
        radius = np.mean(distances)
        return distances - radius

    result = least_squares(residuals, initial_guess, method='lm')
    return result.x


def compute_correction_matrix(params):
    x0, y0, z0, a, b, c, alpha, beta, gamma = params
    # Translation vector for hard iron correction
    translation = np.array([x0, y0, z0])

    # Create rotation matrices for each axis
    Rx = np.array([[1, 0, 0],
                   [0, np.cos(alpha), -np.sin(alpha)],
                   [0, np.sin(alpha), np.cos(alpha)]])
    Ry = np.array([[np.cos(beta), 0, np.sin(beta)],
                   [0, 1, 0],
                   [-np.sin(beta), 0, np.cos(beta)]])
    Rz = np.array([[np.cos(gamma), -np.sin(gamma), 0],
                   [np.sin(gamma), np.cos(gamma), 0],
                   [0, 0, 1]])
    # Complete rotation matrix
    R = Rx @ Ry @ Rz

    # Scaling matrix for soft iron correction
    S = np.diag([1 / a, 1 / b, 1 / c])

    # The full correction matrix
    correction_matrix = R @ S
    return translation, correction_matrix


def apply_calibration(mag_data, translation, correction_matrix):
    # Correct hard iron distortions
    corrected_data = mag_data - translation[:, np.newaxis]
    # Correct soft iron distortions
    corrected_data = correction_matrix @ corrected_data
    return corrected_data

