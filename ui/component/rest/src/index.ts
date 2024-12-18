import {ApiClient} from "./restclient";



export class RestApi {

    protected _apiClient: ApiClient;

    constructor(apiClient: ApiClient) {
        this._apiClient = apiClient;
    }

    get api() {
        return this._apiClient;
    }
}

export default new RestApi(new ApiClient());
