/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.telephony.satellite.stub;

import android.telephony.satellite.stub.NtnSignalStrength;

/**
 * Consumer pattern for a request that receives the signal strength of non-terrestrial network from
 * the SatelliteService.
 * @hide
 */
oneway interface INtnSignalStrengthConsumer {
    void accept(in NtnSignalStrength result);
}