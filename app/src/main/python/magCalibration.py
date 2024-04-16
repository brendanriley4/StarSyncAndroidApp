import numpy as np
from scipy import linalg


class Magnetometer(object):
    def __init__(self, F=1000):
        # Initialize magnetic field strength and calibration parameters
        self.F = F
        self.b = np.zeros([3, 1])
        self.A_1 = np.eye(3)

    def process_data(self, data_string):
        # Convert string data to numpy array format
        data_list = data_string.split(',')
        grouped_data = [data_list[n:n+3] for n in range(0, len(data_list), 3)]
        data = np.array([list(map(float, group)) for group in grouped_data])
        return data.T  # Transpose for subsequent processing

    def calibrate(self, data_string):
        # Process the string data
        data = self.process_data(data_string)
        # Perform ellipsoid fitting
        M, n, d = self.__ellipsoid_fit(data)
        # Compute calibration parameters
        M_1 = linalg.inv(M)
        self.b = -np.dot(M_1, n)
        self.A_1 = np.real(self.F / np.sqrt(np.dot(n.T, np.dot(M_1, n)) - d) * linalg.sqrtm(M))

        # format output
        string_output = matrices_to_csv(self.A_1, self.b)

        return string_output

    def __ellipsoid_fit(self, s):

        # D (samples)
        D = np.array([s[0] ** 2., s[1] ** 2., s[2] ** 2.,
                      2. * s[1] * s[2], 2. * s[0] * s[2], 2. * s[0] * s[1],
                      2. * s[0], 2. * s[1], 2. * s[2], np.ones_like(s[0])])

        # S, S_11, S_12, S_21, S_22 (eq. 11)
        S = np.dot(D, D.T)
        S_11 = S[:6, :6]
        S_12 = S[:6, 6:]
        S_21 = S[6:, :6]
        S_22 = S[6:, 6:]

        # C (Eq. 8, k=4)
        C = np.array([[-1, 1, 1, 0, 0, 0],
                      [1, -1, 1, 0, 0, 0],
                      [1, 1, -1, 0, 0, 0],
                      [0, 0, 0, -4, 0, 0],
                      [0, 0, 0, 0, -4, 0],
                      [0, 0, 0, 0, 0, -4]])

        # v_1 (eq. 15, solution)
        E = np.dot(linalg.inv(C),
                   S_11 - np.dot(S_12, np.dot(linalg.inv(S_22), S_21)))

        E_w, E_v = np.linalg.eig(E)

        v_1 = E_v[:, np.argmax(E_w)]
        if v_1[0] < 0: v_1 = -v_1

        # v_2 (eq. 13, solution)
        v_2 = np.dot(np.dot(-np.linalg.inv(S_22), S_21), v_1)

        # quadratic-form parameters, parameters h and f swapped as per correction by Roger R on Teslabs page
        M = np.array([[v_1[0], v_1[5], v_1[4]],
                      [v_1[5], v_1[1], v_1[3]],
                      [v_1[4], v_1[3], v_1[2]]])
        n = np.array([[v_2[0]],
                      [v_2[1]],
                      [v_2[2]]])
        d = v_2[3]

        return M, n, d


def matrices_to_csv(A, b):
    # Convert matrices to numpy arrays if they are not already
    A = np.array(A)
    b = np.array(b)

    # Flatten the arrays
    flat_A = A.flatten()
    flat_b = b.flatten()

    # Concatenate into one array
    full_array = np.concatenate((flat_A, flat_b))

    # Convert to 16-bit floating point
    full_array_16bit = full_array.astype(np.float16)

    # Convert to comma-separated string
    csv_string = ",".join(map(str, full_array_16bit))

    return csv_string
