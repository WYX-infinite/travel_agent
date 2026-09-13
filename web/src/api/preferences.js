import { http } from '../plugins/axios'

const endpoint = '/api/user/travel-preferences'
export function getTravelPreferences() { return http.get(endpoint) }
export function replaceTravelPreferences(payload) { return http.put(endpoint, payload) }
export function clearTravelPreferences() { return http.delete(endpoint) }
