import { Routes } from '@angular/router';
import { LiveDashboardPage } from './live-dashboard-page';
import { StartPage } from './start-page';
import { AoaPage } from './aoa-page';

export const routes: Routes = [
	{ path: '', pathMatch: 'full', redirectTo: 'start' },
	{ path: 'start', component: StartPage },
	{ path: 'dashboard', pathMatch: 'full', redirectTo: 'waterfall' },
	{ path: 'waterfall', component: LiveDashboardPage },
	{ path: 'aoa', component: AoaPage },
	{ path: '**', redirectTo: 'start' },
];
